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
import cats.effect.Sync
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.{AnonymousIpResponse, CityResponse}
import com.snowplowanalytics.lrumap.LruMap
import com.snowplowanalytics.maxmind.iplookups.model._

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
   * @param lruCacheSize Maximum size of LruMap cache
   */
  def createFromFiles[F[_]: Sync](
    geoFile: Option[File] = None,
    ispFile: Option[File] = None,
    domainFile: Option[File] = None,
    connectionTypeFile: Option[File] = None,
    anonymousIpFile: Option[File] = None,
    memCache: Boolean = true,
    lruCacheSize: Int = 10000
  ): F[IpLookups[F]] =
    (
      if (lruCacheSize > 0)
        Sync[F].map(LruMap.create[F, String, IpLookupResult](lruCacheSize))(Some(_))
      else Sync[F].pure(None)
    ).flatMap((lruCache) =>
      Sync[F].delay {
        new IpLookups(
          geoFile,
          ispFile,
          domainFile,
          connectionTypeFile,
          anonymousIpFile,
          memCache,
          lruCache
        )
    })

  /**
   * Alternative constructor taking Strings rather than Files
   *
   * @param geoFile Geographic lookup database filepath
   * @param ispFile ISP lookup database filepath
   * @param domainFile Domain lookup database filepath
   * @param connectionTypeFile Connection type lookup database filepath
   * @param memCache Whether to use MaxMind's CHMCache
   * @param lruCacheSize Maximum size of LruMap cache
   */
  def createFromFilenames[F[_]: Sync](
    geoFile: Option[String] = None,
    ispFile: Option[String] = None,
    domainFile: Option[String] = None,
    connectionTypeFile: Option[String] = None,
    anonymousIpFile: Option[String] = None,
    memCache: Boolean = true,
    lruCacheSize: Int = 10000
  ): F[IpLookups[F]] =
    IpLookups.createFromFiles(
      geoFile.map(new File(_)),
      ispFile.map(new File(_)),
      domainFile.map(new File(_)),
      connectionTypeFile.map(new File(_)),
      anonymousIpFile.map(new File(_)),
      memCache,
      lruCacheSize
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
class IpLookups[F[_]: Sync] private (
  geoFile: Option[File],
  ispFile: Option[File],
  domainFile: Option[File],
  connectionTypeFile: Option[File],
  anonymousIpFile: Option[File],
  memCache: Boolean,
  lru: Option[LruMap[F, String, IpLookupResult]]
) {
  // Configure the lookup services
  private val geoService = getService(geoFile)
  private val ispService = getService(ispFile).map(SpecializedReader(_, ReaderFunctions.isp))
  private val orgService = getService(ispFile).map(SpecializedReader(_, ReaderFunctions.org))
  private val domainService =
    getService(domainFile).map(SpecializedReader(_, ReaderFunctions.domain))
  private val connectionTypeService =
    getService(connectionTypeFile).map(SpecializedReader(_, ReaderFunctions.connectionType))
  private val anonymousIpService = getService(anonymousIpFile)

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
   * Creates an Either from an IPLookup
   * @param service ISP, domain or connection type LookupService
   * @return the result of the lookup
   */
  private def getLookup(
    ipAddress: Either[Throwable, InetAddress],
    service: Option[SpecializedReader]
  ): F[Option[Either[Throwable, String]]] =
    (ipAddress, service) match {
      case (Right(ipA), Some(svc)) =>
        Sync[F].map(svc.getValue(ipA))(Some(_))
      case (Left(f), _) =>
        Sync[F].pure(Some(Left(f)))
      case _ =>
        Sync[F].pure(None)
    }

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  def performLookups(s: String): F[IpLookupResult] =
    lru
      .map(performLookupsWithLruCache(_, s))
      .getOrElse(performLookupsWithoutLruCache(s))

  private def getLocationLookup(
    ipAddress: Either[Throwable, InetAddress]
  ): F[Option[Either[Throwable, IpLocation]]] = (ipAddress, geoService) match {
    case (Right(ipA), Some(gs)) =>
      Sync[F].map(getCityResponse(gs, ipA))(
        (loc) => Some(loc.map(IpLocation(_)))
      )
    case (Left(f), _) => Sync[F].pure(Some(Left(f)))
    case _            => Sync[F].pure(None)
  }

  private def getAnonymousIpResponse(
    ipAddress: Either[Throwable, InetAddress]
  ): F[Option[Either[Throwable, AnonymousIp]]] = (ipAddress, anonymousIpService) match {
    case (Right(ipA), Some(gs)) =>
      Sync[F].map(getAnonymousIpResponse(gs, ipA))(
        (loc) => Some(loc.map(AnonymousIp(_)))
      )
    case (Left(f), _) => Sync[F].pure(Some(Left(f)))
    case _            => Sync[F].pure(None)
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
  private def performLookupsWithoutLruCache(ip: String): F[IpLookupResult] =
    for {
      ipAddress <- getIpAddress(ip)

      ipLocation     <- getLocationLookup(ipAddress)
      isp            <- getLookup(ipAddress, ispService)
      org            <- getLookup(ipAddress, orgService)
      domain         <- getLookup(ipAddress, domainService)
      connectionType <- getLookup(ipAddress, connectionTypeService)
      anonymousIp    <- getAnonymousIpResponse(ipAddress)
    } yield IpLookupResult(ipLocation, isp, org, domain, connectionType, anonymousIp)

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
    lru: LruMap[F, String, IpLookupResult],
    ip: String
  ): F[IpLookupResult] = {
    val lookupAndCache = performLookupsWithoutLruCache(ip).flatMap { result =>
      lru.put(ip, result).map(_ => result)
    }

    lru
      .get(ip)
      .map(_.map(Sync[F].pure(_)))
      .flatMap(_.getOrElse(lookupAndCache))
  }

  /** Transforms a String into an Either[Throwable, InetAddress] */
  private def getIpAddress(ip: String): F[Either[Throwable, InetAddress]] =
    Sync[F].delay { Either.catchNonFatal(InetAddress.getByName(ip)) }

  private def getCityResponse(
    gs: DatabaseReader,
    ipAddress: InetAddress
  ): F[Either[Throwable, CityResponse]] =
    Sync[F].delay { Either.catchNonFatal(gs.city(ipAddress)) }

  private def getAnonymousIpResponse(
    gs: DatabaseReader,
    ipAddress: InetAddress
  ): F[Either[Throwable, AnonymousIpResponse]] =
    Sync[F].delay { Either.catchNonFatal(gs.anonymousIp(ipAddress)) }
}
