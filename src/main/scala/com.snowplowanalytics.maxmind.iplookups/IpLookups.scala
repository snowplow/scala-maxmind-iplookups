/*
 * Copyright (c) 2012-2018 Snowplow Analytics Ltd. All rights reserved.
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

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.DatabaseReader
import com.twitter.util.SynchronizedLruMap
import cats.syntax.either._
import cats.effect.IO
import scalaz._

import model._

/** Companion object to hold alternative constructors. */
object IpLookups {

  /**
   * Create an IpLookups from Files
   *
   * @param geoFile Geographic lookup database file
   * @param ispFile ISP lookup database file
   * @param domainFile Domain lookup database file
   * @param connectionTypeFile Connection type lookup database file
   * @param memCache Whether to use MaxMind's CHMCache
   * @param lruCache Maximum size of SynchronizedLruMap cache
   */
  def createFromFiles(
    geoFile: Option[File] = None,
    ispFile: Option[File] = None,
    domainFile: Option[File] = None,
    connectionTypeFile: Option[File] = None,
    memCache: Boolean = true,
    lruCache: Int = 10000
  ): IO[IpLookups] = IO {
    new IpLookups(
      geoFile,
      ispFile,
      domainFile,
      connectionTypeFile,
      memCache,
      lruCache
    )
  }

  /**
   * Alternative constructor taking Strings rather than Files
   *
   * @param geoFile Geographic lookup database filepath
   * @param ispFile ISP lookup database filepath
   * @param domainFile Domain lookup database filepath
   * @param connectionTypeFile Connection type lookup database filepath
   * @param memCache Whether to use MaxMind's CHMCache
   * @param lruCache Maximum size of SynchronizedLruMap cache
   */
  def createFromFilenames(
    geoFile: Option[String] = None,
    ispFile: Option[String] = None,
    domainFile: Option[String] = None,
    connectionTypeFile: Option[String] = None,
    memCache: Boolean = true,
    lruCache: Int = 10000
  ): IO[IpLookups] =
    IpLookups.createFromFiles(
      geoFile.map(new File(_)),
      ispFile.map(new File(_)),
      domainFile.map(new File(_)),
      connectionTypeFile.map(new File(_)),
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
 */
class IpLookups private (
  geoFile: Option[File] = None,
  ispFile: Option[File] = None,
  domainFile: Option[File] = None,
  connectionTypeFile: Option[File] = None,
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
  private val orgService = getService(ispFile).map(SpecializedReader(_, ReaderFunctions.org))
  private val domainService =
    getService(domainFile).map(SpecializedReader(_, ReaderFunctions.domain))
  private val connectionTypeService =
    getService(connectionTypeFile).map(SpecializedReader(_, ReaderFunctions.connectionType))

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
   * Creates a Validation from an IPLookup
   * @param service ISP, domain or connection type LookupService
   * @return the result of the lookup
   */
  private def getLookup(
    ipAddress: Validation[Throwable, InetAddress],
    service: Option[SpecializedReader]
  ): IO[Option[Validation[Throwable, String]]] =
    (ipAddress, service) match {
      case (Success(ipA), Some(svc)) =>
        svc.getValue(ipA).map(Some(_))
      case (Failure(f), _) =>
        IO.pure(Some(Failure(f)))
      case _ =>
        IO.pure(None)
    }

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  val performLookups: String => IO[IpLookupResult] = (s: String) =>
    lru
      .map(performLookupsWithLruCache(_, s))
      .getOrElse(performLookupsWithoutLruCache(s))

  private def getLocationLookup(
    ipAddress: Validation[Throwable, InetAddress]
  ): IO[Option[Validation[Throwable, IpLocation]]] = (ipAddress, geoService) match {
    case (Success(ipA), Some(gs)) =>
      (getCityResponse(gs, ipA)).map(
        (loc) => Some(loc.map(IpLocation(_)))
      )
    case (Failure(f), _) => IO.pure(Some(Failure(f)))
    case _               => IO.pure(None)
  }

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
  private def performLookupsWithoutLruCache(ip: String): IO[IpLookupResult] = {
    for {
      ipAddress <- getIpAddress(ip)

      ipLocation     <- getLocationLookup(ipAddress)
      isp            <- getLookup(ipAddress, ispService)
      org            <- getLookup(ipAddress, orgService)
      domain         <- getLookup(ipAddress, domainService)
      connectionType <- getLookup(ipAddress, connectionTypeService)
    } yield IpLookupResult(ipLocation, isp, org, domain, connectionType)
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
  ): IO[IpLookupResult] = {
    val lookupAndCache = for {
      result <- performLookupsWithoutLruCache(ip)
      _      <- putFromMap(lru, ip, result)
    } yield result

    getFromMap(lru, ip)
      .map(_.map(IO.pure(_)))
      .flatMap(_.getOrElse(lookupAndCache))
  }

  /** Transforms a String into an Validation[Throwable, InetAddress] */
  private def getIpAddress(ip: String): IO[Validation[Throwable, InetAddress]] =
    IO { Validation.fromTryCatch(InetAddress.getByName(ip)) }

  private def getCityResponse(
    gs: DatabaseReader,
    ipAddress: InetAddress
  ): IO[Validation[Throwable, CityResponse]] =
    IO { Validation.fromTryCatch(gs.city(ipAddress)) }

  private def getFromMap[K, V](map: SynchronizedLruMap[K, V], k: K): IO[Option[V]] =
    IO { map.get(k) }

  private def putFromMap[K, V](map: SynchronizedLruMap[K, V], k: K, v: V): IO[Unit] =
    IO { map.put(k, v) }
}
