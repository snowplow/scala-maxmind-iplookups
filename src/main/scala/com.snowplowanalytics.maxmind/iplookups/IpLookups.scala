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
import java.net.InetAddress

import com.maxmind.db.CHMCache

// LRU
import com.twitter.util.SynchronizedLruMap

// MaxMind
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.GeoIp2Exception

// Concurrency
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global

// This library
import IpLocation._

/**
 * Companion object to hold alternative constructors.
 *
 */
object IpLookups {

  /**
   * Alternative constructor taking Strings rather than Files
   *
   * @param geoFile Geographic lookup database filepath
   * @param ispFile ISP lookup database filepath
   * @param orgFile Organization lookup database filepath
   * @param domainFile Domain lookup database filepath
   * @param netspeedFile Net speed lookup database filepath
   * @param memCache Whether to use the GEO_IP_MEMORY_CACHE
   * @param lruCache Maximum size of SynchronizedLruMap cache
   */
  def apply(geoFile: Option[String] = None, ispFile: Option[String] = None, orgFile: Option[String] = None,
            domainFile: Option[String] = None, netspeedFile: Option[String] = None,
            memCache: Boolean = true, lruCache: Int = 10000) = {    
    new IpLookups(geoFile.map(new File(_)), ispFile.map(new File(_)), orgFile.map(new File(_)), 
                  domainFile.map(new File(_)), netspeedFile.map(new File(_)),
                  memCache, lruCache)
  }
}

/**
 * IpLookups is a Scala wrapper around MaxMind's own DatabaseReader Java class.
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
 * @param geoFile Geographic lookup database file
 * @param ispFile ISP lookup database file
 * @param orgFile Organization lookup database file
 * @param domainFile Domain lookup database file
 * @param netspeedFile Net speed lookup database file
 * @param memCache Whether to use the GEO_IP_MEMORY_CACHE
 * @param lruCache Maximum size of SynchronizedLruMap cache
 */
class IpLookups(geoFile: Option[File] = None, ispFile: Option[File] = None, orgFile: Option[File] = None, 
                domainFile: Option[File] = None, netspeedFile: Option[File] = None,
                memCache: Boolean = true, lruCache: Int = 10000) {

  // Initialise the cache
  private val lru = if (lruCache > 0) new SynchronizedLruMap[String, IpLookupResult](lruCache) else null // Of type mutable.Map[String, LookupData]

  // Configure the lookup services
  private val geoService = getService(geoFile)
  private val ispService = getService(ispFile).map(SpecializedReader(_, ReaderFunctions.isp))
  private val orgService = getService(orgFile).map(SpecializedReader(_, ReaderFunctions.org))
  private val domainService = getService(domainFile).map(SpecializedReader(_, ReaderFunctions.domain))
  private val netspeedService = getService(netspeedFile).map(SpecializedReader(_, ReaderFunctions.netSpeed))

  /**
   * Get a LookupService from a database file
   *
   * @param serviceFile The database file
   * @return LookupService
   */
  private def getService(serviceFile: Option[File]): Option[DatabaseReader] =
    serviceFile.map(f => {
      val builder = new DatabaseReader.Builder(f)
      (
        if (memCache)
          builder.withCache(new CHMCache())
        else
          builder
      ).build()
    })

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  def performLookups = if (lruCache <= 0) performLookupsWithoutLruCache _ else performLookupsWithLruCache _

  /**
   * This version does not use the LRU cache.
   * Concurrently looks up information
   * based on an IP address from one or
   * more MaxMind LookupServices
   *
   * @param ip IP address
   * @return Tuple containing the results of the
   *         LookupServices   
   */
  private def performLookupsWithoutLruCache(ip: String): IpLookupResult = {

    /**
     * Creates a Future boxing the result
     * of using a lookup service on the ip
     *
     * @param service ISP, organization,
     *        domain or net speed LookupService
     * @return the result of the lookup
     */
    def getLookupFuture(service: Option[SpecializedReader]): Future[Option[String]] =
      Future {
        service.flatMap(_.getValue(getIpAddress(ip)))
      }

    val geoFuture: Future[Option[IpLocation]] = Future {
      // gs.getLocation(ip) must be wrapped in a Option in case it is null
      geoService.flatMap(gs => Option(gs.city(getIpAddress(ip)))).map(IpLocation.apply(_))
    }

    val aggregateFuture: Future[IpLookupResult] = for {
      geoResult       <- geoFuture
      ispResult       <- getLookupFuture(ispService)
      orgResult       <- getLookupFuture(orgService)
      domainResult    <- getLookupFuture(domainService)
      netspeedResult  <- getLookupFuture(netspeedService)
    } yield (geoResult, ispResult, orgResult, domainResult, netspeedResult)

    try {
      Await.result(aggregateFuture, 4.seconds)
    } catch {
      case ge: GeoIp2Exception => (None, None, None, None, None)
      case te: TimeoutException => (None, None, None, None, None)
      case iae: IllegalArgumentException => (None, None, None, None, None)
      case e: Exception => throw e
    }
  }

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
  private def performLookupsWithLruCache(ip: String): IpLookupResult = lru.get(ip) match {
    case Some(result) => result // In the LRU cache
    case None => // Not in the LRU cache
      val result = performLookupsWithoutLruCache(ip)
      lru.put(ip, result)
      result
  }

  /**
    * Transforms a String into an InetAddress
    */
  private def getIpAddress(ip: String): InetAddress = InetAddress.getByName(ip)
}
