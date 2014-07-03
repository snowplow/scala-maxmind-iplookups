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
package com.snowplowanalytics.maxmind.geoip

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
object IpGeo {

  /**
   * Alternative constructor taking a String rather than File
   */
  def apply(dbFile: String, memCache: Boolean = true, lruCache: Int = 10000, 
            ispFile: Option[String] = None, orgFile: Option[String] = None, domainFile: Option[String] = None) = {
    new IpGeo(new File(dbFile), memCache, lruCache, ispFile, orgFile, domainFile)
  }
}

/**
 * IpGeo is a Scala wrapper around MaxMind's own LookupService Java class.
 *
 * Two main differences:
 *
 * 1. getLocation(ip: String) now returns an IpLocation
 *    case class, not a raw MaxMind Location
 * 2. IpGeo introduces an LRU cache to improve
 *    lookup performance
 *
 * Inspired by:
 * https://github.com/jt6211/hadoop-dns-mining/blob/master/src/main/java/io/covert/dns/geo/IpGeo.java
 */
class IpGeo(dbFile: File, memCache: Boolean = true, lruCache: Int = 10000,
            ispFile: Option[String] = None, orgFile: Option[String] = None, domainFile: Option[String] = None) {

  // Initialise the cache
  private val lru = if (lruCache > 0) new LruMap[String, IpLookupResult](lruCache) else null // Of type mutable.Map[String, LookupData]

  // Configure the lookup services
  private val options = if (memCache) LookupService.GEOIP_MEMORY_CACHE else LookupService.GEOIP_STANDARD
  private val maxmind = new LookupService(dbFile, options)
  private val ispService: Option[LookupService] = ispFile.map(new LookupService(_, options))
  private val orgService: Option[LookupService] = orgFile.map(new LookupService(_, options))
  private val domainService: Option[LookupService] = domainFile.map(new LookupService(_, options))

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
    performLookups(ip, maxmind, ispService, orgService, domainService)

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
    case Some(loc) => loc // In the LRU cache
    case None => // Not in the LRU cache
      val loc = getLocationWithoutLruCache(ip)
      lru.put(ip, loc)
      loc
  }

  /**
   * Concurrently looks up information
   * based on an IP address from one or
   * more MaxMind LookupServices
   *
   * @param ip IP address
   * @param maxmind Location LookupService
   * @param ispService ISP LookupService
   * @param orgService Organization LookupService
   * @param domainService Domain LookupService
   * @return Tuple containing the results of the
   *         LookupServices
   */
  def performLookups(ip: String, maxmind: LookupService, ispService: Option[LookupService], orgService: Option[LookupService], domainService: Option[LookupService]): IpLookupResult = {

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

    val maxmindFuture = Future {
      Option(maxmind.getLocation(ip)).map(IpLocation.apply(_))
    }

    val aggregateFuture: Future[IpLookupResult] = for {
      maxmindResult <- maxmindFuture
      ispResult     <- getLookupFuture(ispService)
      orgResult     <- getLookupFuture(orgService)
      domainResult  <- getLookupFuture(domainService)
    } yield (maxmindResult, ispResult, orgResult, domainResult)

    try {
      Await.result(aggregateFuture, 4.seconds)
    } catch {
      case te: TimeoutException => (None, None, None, None)
      case e: Exception => throw e
    }
  }  
}
