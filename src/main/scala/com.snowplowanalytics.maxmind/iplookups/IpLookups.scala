/*
 * Copyright (c) 2012-2013 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.maxmind.iplookups

// Java
import java.io.File

// LRU
import com.twitter.util.LruMap

// MaxMind
import com.maxmind.geoip.{Location, LookupService}

// Concurrency
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

// This library
import IpLocation._

/**
 * Companion object to hold alternative constructors.
 *
 */
object IpLookups {

  /**
   * Alternative constructor taking Strings rather than Files
   */
  def apply(geoFile: Option[String] = None, ispFile: Option[String] = None, orgFile: Option[String] = None, domainFile: Option[String] = None,
            memCache: Boolean = true, lruCache: Int = 10000) = {    
    new IpLookups(geoFile.map(new File(_)), ispFile.map(new File(_)), orgFile.map(new File(_)), domainFile.map(new File(_)),
                  memCache, lruCache)
  }
}

/**
 * IpLookups is a Scala wrapper around MaxMind's own LookupService Java class.
 *
 * Two main differences:
 *
 * 1. getLocation(ip: String) now returns an IpLocation
 *    case class, not a raw MaxMind Location
 * 2. IpLookups introduces an LRU cache to improve
 *    lookup performance
 *
 * Inspired by:
 * https://github.com/jt6211/hadoop-dns-mining/blob/master/src/main/java/io/covert/dns/geo/IpLookups.java
 *
 * @param geoFile Geographic lookup file
 * @param ispFile ISP lookup file
 * @param orgFile Organization lookup file
 * @param domainFile Domain lookup file
 * @param memCache Whether to use the GEO_IP_MEMORY_CACHE
 * @param lruCache Maximum size of LruMap cache
 */
class IpLookups(geoFile: Option[File] = None, ispFile: Option[File] = None, orgFile: Option[File] = None, domainFile: Option[File] = None,
                memCache: Boolean = true, lruCache: Int = 10000) {

  // Initialise the cache
  private val lru = if (lruCache > 0) new LruMap[String, IpLookupResult](lruCache) else null // Of type mutable.Map[String, LookupData]

  // Configure the lookup services
  private val options = if (memCache) LookupService.GEOIP_MEMORY_CACHE else LookupService.GEOIP_STANDARD
  private val geoService = getService(geoFile)
  private val ispService = getService(ispFile)
  private val orgService = getService(orgFile)
  private val domainService = getService(domainFile)

  /**
   * Get a LookupService from a database file
   *
   * @param serviceFile The database file
   * @return LookupService
   */
  private def getService(serviceFile: Option[File]): Option[LookupService] = 
    serviceFile.map(new LookupService(_, options))

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  def getLocation = if (lruCache <= 0) getLocationWithoutLruCache _ else getLocationWithLruCache _

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   *
   * This version does not use the LRU cache.
   */
  private def getLocationWithoutLruCache(ip: String): IpLookupResult =
    performLookups(ip, geoService, ispService, orgService, domainService)

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   *
   * This version uses and maintains the LRU cache.
   *
   * Don't confuse the LRU returning None (meaning that no
   * cache entry could be found), versus an extant cache entry
   * containing None (meaning that the IP address is unknown).
   */
  private def getLocationWithLruCache(ip: String): IpLookupResult = lru.get(ip) match {
    case Some(result) => result // In the LRU cache
    case None => // Not in the LRU cache
      val result = getLocationWithoutLruCache(ip)
      lru.put(ip, result)
      result
  }

  /**
   * Concurrently looks up information
   * based on an IP address from one or
   * more MaxMind LookupServices
   *
   * @param ip IP address
   * @param geoService Location LookupService
   * @param ispService ISP LookupService
   * @param orgService Organization LookupService
   * @param domainService Domain LookupService
   * @return Tuple containing the results of the
   *         LookupServices
   */
  private def performLookups(ip: String, geoService: Option[LookupService], ispService: Option[LookupService], orgService: Option[LookupService], domainService: Option[LookupService]): IpLookupResult = {

    /**
     * Creates a Future boxing the result
     * of using a lookup service on the ip
     *
     * @param service ISP, organization,
     *        or domain LookupService
     * @return the result of the lookup
     */
    def getLookupFuture(service: Option[LookupService]): Future[Option[String]] = 
      Future {
        service.map(_.getOrg(ip)).filter(_ != null)
      }

    val geoFuture: Future[Option[IpLocation]] = Future {

      // gs.getLocation(ip) must be wrapped in a Option in case it is null
      geoService.flatMap(gs => Option(gs.getLocation(ip))).map(IpLocation.apply(_))
    }

    val aggregateFuture: Future[IpLookupResult] = for {
      geoResult     <- geoFuture
      ispResult     <- getLookupFuture(ispService)
      orgResult     <- getLookupFuture(orgService)
      domainResult  <- getLookupFuture(domainService)
    } yield (geoResult, ispResult, orgResult, domainResult)

    try {
      Await.result(aggregateFuture, 4.seconds)
    } catch {
      case te: TimeoutException => (None, None, None, None)
      case e: Exception => throw e
    }
  }
}
