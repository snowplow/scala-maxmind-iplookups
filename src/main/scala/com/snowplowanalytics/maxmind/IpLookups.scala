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
package com.snowplowanalytics.maxmind

import java.io.File
import com.maxmind.geoip.LookupService
import com.micronautics.concurrent.SynchronizedLruMap
import scala.concurrent._
import scala.concurrent.duration._

object IpLookups {
  private var lru: SynchronizedLruMap[String, IpLookupResult] = _

  /** Alternative constructor taking Strings rather than Files
   * @param geoFile Geographic lookup database file path
   * @param ispFile ISP lookup database file path
   * @param orgFile Organization lookup database file path
   * @param domainFile Domain lookup database file path
   * @param netSpeedFile Net speed lookup database file path
   * @param memCache Whether to use the GEO_IP_MEMORY_CACHE
   * @param lruCache Maximum size of SynchronizedLruMap cache */
  def apply(
    geoFile: Option[String] = None,
    ispFile: Option[String] = None,
    orgFile: Option[String] = None,
    domainFile: Option[String] = None,
    netSpeedFile: Option[String] = None,
    memCache: Boolean = true,
    lruCache: Int = 10000
  )(implicit executionContext: ExecutionContext): IpLookups = {
    lru = new SynchronizedLruMap[String, IpLookupResult](lruCache)
    new IpLookups(
      geoFile.map(new File(_)),
      ispFile.map(new File(_)),
      orgFile.map(new File(_)),
      domainFile.map(new File(_)),
      netSpeedFile.map(new File(_)),
      memCache
    )
  }
}

/** IpLookups is a Scala wrapper around MaxMind's own LookupService Java class.
 * Two main differences:
 *
 * 1. getLocation(ip: String) now returns an IpLocation case class, not a raw MaxMind Location
 * 2. IpLookups introduces an LRU cache to improve lookup performance
 *
 * Inspired by:
 * https://github.com/jt6211/hadoop-dns-mining/blob/master/src/main/java/io/covert/dns/geo/IpLookups.java
 *
 * @param geoFile Geographic lookup database file
 * @param ispFile ISP lookup database file
 * @param orgFile Organization lookup database file
 * @param domainFile Domain lookup database file
 * @param netSpeedFile Net speed lookup database file
 * @param memCache Whether to use the GEO_IP_MEMORY_CACHE */
class IpLookups(
  geoFile: Option[File] = None,
  ispFile: Option[File] = None,
  orgFile: Option[File] = None,
  domainFile: Option[File] = None,
  netSpeedFile: Option[File] = None,
  memCache: Boolean = true
)(implicit ec: ExecutionContext) {
  // Configure the lookup services
  private val options = if (memCache) LookupService.GEOIP_MEMORY_CACHE else LookupService.GEOIP_STANDARD
  private val geoService = getService(geoFile)
  private val ispService = getService(ispFile)
  private val orgService = getService(orgFile)
  private val domainService = getService(domainFile)
  private val netspeedService = getService(netSpeedFile)

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
  def performLookups: String => IpLookupResult =
    if (IpLookups.lru.underlying.size <= 0) lookupWithoutLruCache else lookupWithLruCache

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
  private def lookupWithoutLruCache(ip: String): IpLookupResult = {
    /** Creates a Future boxing the result of using a lookup service on the ip
     * @param service ISP, organization, domain or net speed LookupService
     * @return the result of the lookup */
    def getLookupFuture(service: Option[LookupService]): Future[Option[String]] =
      Future {
        service.map(_.getOrg(ip)).filter(_ != null)
      }

    val geoFuture: Future[Option[IpLocation]] = Future {
      // gs.getLocation(ip) must be wrapped in a Option in case it is null
      geoService.flatMap(gs => Option(gs.getLocation(ip))).map(IpLocation.apply)
    }

    val aggregateFuture: Future[IpLookupResult] = for {
      geoResult       <- geoFuture
      ispResult       <- getLookupFuture(ispService)
      orgResult       <- getLookupFuture(orgService)
      domainResult    <- getLookupFuture(domainService)
      netSpeedResult  <- getLookupFuture(netspeedService)
    } yield IpLookupResult(geoResult, ispResult, orgResult, domainResult, netSpeedResult)

    try {
      Await.result(aggregateFuture, 4.seconds)
    } catch {
      case _: TimeoutException         => IpLookupResult()
      case _: IllegalArgumentException => IpLookupResult()
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
  private def lookupWithLruCache(ip: String): IpLookupResult =
    Option(IpLookups.lru.underlying.asMap.get(ip)) match {
      case Some(result) => result // In the LRU cache
      case None => // Not in the LRU cache
        val result = lookupWithoutLruCache(ip)
        IpLookups.lru.put(ip, result)
        result
    }
}
