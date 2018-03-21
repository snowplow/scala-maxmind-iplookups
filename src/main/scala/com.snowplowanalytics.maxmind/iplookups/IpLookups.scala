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

import java.io.File
import java.net.InetAddress
import java.util.NoSuchElementException

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.GeoIp2Exception
import com.maxmind.geoip2.model.CityResponse
import com.twitter.util.SynchronizedLruMap
import scalaz._
import Scalaz._

import IpLocation._

/** Companion object to hold alternative constructors. */
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
  def apply(
    geoFile: Option[String] = None,
    ispFile: Option[String] = None,
    orgFile: Option[String] = None,
    domainFile: Option[String] = None,
    netspeedFile: Option[String] = None,
    memCache: Boolean = true,
    lruCache: Int = 10000
  ): IpLookups =
    new IpLookups(
      geoFile.map(new File(_)),
      ispFile.map(new File(_)),
      orgFile.map(new File(_)),
      domainFile.map(new File(_)),
      netspeedFile.map(new File(_)),
      memCache,
      lruCache
    )
}

/**
 * IpLookups is a Scala wrapper around MaxMind's own DatabaseReader Java class.
 *
 * Two main differences:
 *
 * 1. getLocation(ipS: String) now returns an IpLocation
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
class IpLookups(
  geoFile: Option[File] = None,
  ispFile: Option[File] = None,
  orgFile: Option[File] = None,
  domainFile: Option[File] = None,
  netspeedFile: Option[File] = None,
  memCache: Boolean = true,
  lruCache: Int = 10000
) {

  // Initialise the cache
  private val lru =
    if (lruCache > 0) Some(new SynchronizedLruMap[String, IpLookupResult](lruCache))
    else None // Of type mutable.Map[String, LookupData]

  // Configure the lookup services
  private val geoService = getService(geoFile)
  private val ispService = getService(ispFile).map(SpecializedReader(_, ReaderFunctions.isp))
  private val orgService = getService(orgFile).map(SpecializedReader(_, ReaderFunctions.org))
  private val domainService =
    getService(domainFile).map(SpecializedReader(_, ReaderFunctions.domain))
  private val netspeedService =
    getService(netspeedFile).map(SpecializedReader(_, ReaderFunctions.netSpeed))

  /**
   * Get a LookupService from a database file
   *
   * @param serviceFile The database file
   * @return LookupService
   */
  private def getService(serviceFile: Option[File]): Option[DatabaseReader] =
    serviceFile.map { f =>
      val builder = new DatabaseReader.Builder(f)
      (
        if (memCache) builder.withCache(new CHMCache())
        else builder
      ).build()
    }

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  val performLookups: String => IpLookupResult = (s: String) =>
    lru.map(performLookupsWithLruCache(_, s))
      .getOrElse(performLookupsWithoutLruCache(s))

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

    val ipAddress = getIpAddress(ip)

    /**
     * Creates a Future boxing the result
     * of using a lookup service on the ipS
     *
     * @param service ISP, organization,
     *        domain or net speed LookupService
     * @return the result of the lookup
     */
    def getLookup(service: Option[SpecializedReader]): Future[Option[ValidationNel[Throwable, String]]] =
      Future {
        service.map { s =>
          for {
            ipA <- ipAddress
            v <- s.getValue(ipA)
          } yield v
        }
      }

    val ipLocation: Future[Option[ValidationNel[Throwable, IpLocation]]] =
      Future {
        geoService.map { gs =>
          for {
            ipA <- ipAddress
            v <- Validation.fromTryCatch(gs.city(ipA)).toValidationNel[Throwable, CityResponse]
          } yield IpLocation.apply(v)
        }
      }

    val agg = for {
      ip <- ipLocation,
      isp <- getLookup(ispService),
      org <- getLookup(orgService),
      domain <- getLookup(domainService),
      netspeed <- getLookup(netspeedService)
    } yield (ip, isp, org, domain, netspeed)

    Await.result(agg)
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
  private def performLookupsWithLruCache(
    lru: SynchronizedLruMap[String, IpLookupResult],
    ip: String
  ): IpLookupResult = lru.get(ip) match {
    case Some(result) => result // In the LRU cache
    case None => // Not in the LRU cache
      val result = performLookupsWithoutLruCache(ip)
      lru.put(ip, result)
      result
  }

  /** Transforms a String into an Validation[Throwable, InetAddress] */
  private def getIpAddress(ip: String): ValidationNel[Throwable, InetAddress] =
    Validation.fromTryCatch(InetAddress.getByName(ip)).toValidationNel
}
