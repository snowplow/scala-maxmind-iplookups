/*
 * Copyright (c) 2012-2022 Snowplow Analytics Ltd. All rights reserved.
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

import java.io.{File, FileInputStream}
import java.net.InetAddress

import cats.{Eval, Id, Monad}
import cats.effect.Sync
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.option._
import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import com.snowplowanalytics.lrumap.{CreateLruMap, LruMap}

import model._

sealed trait CreateIpLookups[F[_]] {

  /**
   * Create an IpLookups from Files
   *
   * Note: Database initialization performs blocking I/O. In MEMORY mode, the entire database
   * file is read from disk into heap memory during initialization.
   *
   * @param geoFile Geographic lookup database file
   * @param ispFile ISP lookup database file
   * @param domainFile Domain lookup database file
   * @param connectionTypeFile Connection type lookup database file
   * @param anonymousFile Anonymous lookup database file
   * @param asnFile ASN lookup database file
   * @param memCache Whether to use MaxMind's CHMCache
   * @param lruCacheSize Maximum size of LruMap cache
   */
  def createFromFiles(
    geoFile: Option[File] = None,
    ispFile: Option[File] = None,
    domainFile: Option[File] = None,
    connectionTypeFile: Option[File] = None,
    anonymousFile: Option[File] = None,
    asnFile: Option[File] = None,
    memCache: Boolean = true,
    lruCacheSize: Int = 10000
  ): F[IpLookups[F]]

  /**
   * Alternative constructor taking filenames rather than files
   * @param geoFile Geographic lookup database filepath
   * @param ispFile ISP lookup database filepath
   * @param domainFile Domain lookup database filepath
   * @param connectionTypeFile Connection type lookup database filepath
   * @param anonymousFile Anonymous lookup database filepath
   * @param asnFile ASN lookup database filepath
   * @param memCache Whether to use MaxMind's CHMCache
   * @param lruCacheSize Maximum size of LruMap cache
   */
  def createFromFilenames(
    geoFile: Option[String] = None,
    ispFile: Option[String] = None,
    domainFile: Option[String] = None,
    connectionTypeFile: Option[String] = None,
    anonymousFile: Option[String] = None,
    asnFile: Option[String] = None,
    memCache: Boolean = true,
    lruCacheSize: Int = 10000
  ): F[IpLookups[F]] = createFromFiles(
    geoFile.map(new File(_)),
    ispFile.map(new File(_)),
    domainFile.map(new File(_)),
    connectionTypeFile.map(new File(_)),
    anonymousFile.map(new File(_)),
    asnFile.map(new File(_)),
    memCache,
    lruCacheSize
  )
}

object CreateIpLookups {
  def apply[F[_]](implicit ev: CreateIpLookups[F]): CreateIpLookups[F] = ev

  implicit def syncCreateIpLookups[F[_]: Sync](implicit
    CLM: CreateLruMap[F, String, IpLookupResult]
  ): CreateIpLookups[F] = new CreateIpLookups[F] {
    override def createFromFiles(
      geoFile: Option[File] = None,
      ispFile: Option[File] = None,
      domainFile: Option[File] = None,
      connectionTypeFile: Option[File] = None,
      anonymousFile: Option[File] = None,
      asnFile: Option[File] = None,
      memCache: Boolean = true,
      lruCacheSize: Int = 10000
    ): F[IpLookups[F]] =
      (
        if (lruCacheSize > 0) {
          CLM.create(lruCacheSize).map(_.some)
        } else {
          Sync[F].pure(None)
        }
      ).flatMap { lruCache =>
        // Use blocking because IpLookups construction reads entire database files from disk into memory
        Sync[F].blocking {
          new IpLookups(
            geoFile,
            ispFile,
            domainFile,
            connectionTypeFile,
            anonymousFile,
            asnFile,
            memCache,
            lruCache
          )
        }
      }
  }

  implicit def evalCreateIpLookups(implicit
    CLM: CreateLruMap[Eval, String, IpLookupResult]
  ): CreateIpLookups[Eval] = new CreateIpLookups[Eval] {
    override def createFromFiles(
      geoFile: Option[File] = None,
      ispFile: Option[File] = None,
      domainFile: Option[File] = None,
      connectionTypeFile: Option[File] = None,
      anonymousFile: Option[File] = None,
      asnFile: Option[File] = None,
      memCache: Boolean = true,
      lruCacheSize: Int = 10000
    ): Eval[IpLookups[Eval]] =
      (
        if (lruCacheSize > 0) {
          CLM.create(lruCacheSize).map(_.some)
        } else {
          Eval.now(None)
        }
      ).flatMap { lruCache =>
        Eval.later {
          new IpLookups(
            geoFile,
            ispFile,
            domainFile,
            connectionTypeFile,
            anonymousFile,
            asnFile,
            memCache,
            lruCache
          )
        }
      }
  }

  implicit def idCreateIpLookups(implicit
    CLM: CreateLruMap[Id, String, IpLookupResult]
  ): CreateIpLookups[Id] = new CreateIpLookups[Id] {
    override def createFromFiles(
      geoFile: Option[File] = None,
      ispFile: Option[File] = None,
      domainFile: Option[File] = None,
      connectionTypeFile: Option[File] = None,
      anonymousFile: Option[File] = None,
      asnFile: Option[File] = None,
      memCache: Boolean = true,
      lruCacheSize: Int = 10000
    ): Id[IpLookups[Id]] = {
      val lruCache: Option[LruMap[Id, String, IpLookupResult]] =
        if (lruCacheSize > 0) {
          CLM.create(lruCacheSize).map(_.some)
        } else {
          None
        }
      new IpLookups(
        geoFile,
        ispFile,
        domainFile,
        connectionTypeFile,
        anonymousFile,
        asnFile,
        memCache,
        lruCache
      )
    }
  }
}

/**
 * IpLookups is a Scala wrapper around MaxMind's own DatabaseReader Java class.
 * Two main differences:
 * 1. getLocation(ipS: String) now returns an IpLocation
 *    case class, not a raw MaxMind Location
 * 2. IpLookups introduces an LRU cache to improve
 *    lookup performance
 * Inspired by:
 * https://github.com/jt6211/hadoop-dns-mining/blob/master/src/main/java/io/covert/dns/geo/IpLookups.java
 */
class IpLookups[F[_]: Monad] private[iplookups] (
  geoFile: Option[File],
  ispFile: Option[File],
  domainFile: Option[File],
  connectionTypeFile: Option[File],
  anonymousFile: Option[File],
  asnFile: Option[File],
  memCache: Boolean,
  lru: Option[LruMap[F, String, IpLookupResult]]
)(implicit SR: SpecializedReader[F], IAR: IpAddressResolver[F]) {
  // Configure the lookup services
  private val geoService    = getService(geoFile)
  private val ispService    = getService(ispFile)
  private val domainService = getService(domainFile).map((_, ReaderFunctions.domain))
  private val connectionTypeService =
    getService(connectionTypeFile).map((_, ReaderFunctions.connectionType))
  private val anonymousService = getService(anonymousFile)
  private val asnService       = getService(asnFile)

  /**
   * Get a LookupService from a database file
   *
   * Opens the file as a FileInputStream and passes it to DatabaseReader's InputStream-based
   * constructor. This avoids the File-based constructor which uses FileChannel.read into a heap
   * ByteBuffer, causing the JDK to allocate and cache temporary direct ByteBuffers in
   * thread-local storage (sun.nio.ch.Util.BufferCache), leading to off-heap memory growth.
   *
   * @param serviceFile The database file
   * @return LookupService
   */
  private def getService(serviceFile: Option[File]): Option[DatabaseReader] =
    serviceFile.map { f =>
      val stream  = new FileInputStream(f)
      val builder = new DatabaseReader.Builder(stream)
      (
        if (memCache) builder.withCache(new CHMCache())
        else builder
      ).build()
    }

  /**
   * Creates an Either from an IPLookup
   * @param service ISP, domain, connection or anonymous type LookupService
   * @return the result of the lookup
   */
  private def getLookup(
    ipAddress: Either[Throwable, InetAddress],
    service: Option[(DatabaseReader, ReaderFunction[String])]
  ): F[Option[Either[Throwable, String]]] =
    (ipAddress, service) match {
      case (Right(ipA), Some((db, f))) =>
        SR.getValue(f, db, ipA).map(_.some)
      case (Left(f), _) =>
        Monad[F].pure(Some(Left(f)))
      case _ =>
        Monad[F].pure(None)
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
      SR.getValue(ReaderFunctions.city, gs, ipA)
        .map(loc => loc.map(IpLocation(_)).some)
    case (Left(f), _) => Monad[F].pure(Some(Left(f)))
    case _            => Monad[F].pure(None)
  }

  private def getAnonymousIpLookup(
    ipAddress: Either[Throwable, InetAddress]
  ): F[Option[Either[Throwable, AnonymousIp]]] = (ipAddress, anonymousService) match {
    case (Right(ipA), Some(gs)) =>
      SR.getValue(ReaderFunctions.anonymousIp, gs, ipA)
        .map(loc => loc.map(AnonymousIp(_)).some)
    case (Left(f), _) => Monad[F].pure(Some(Left(f)))
    case _            => Monad[F].pure(None)
  }

  private def getIspLookup(
    ipAddress: Either[Throwable, InetAddress]
  ): F[Option[Either[Throwable, Isp]]] = (ipAddress, ispService) match {
    case (Right(ipA), Some(gs)) =>
      SR.getValue(ReaderFunctions.isp, gs, ipA)
        .map(ispResponse => ispResponse.map(Isp(_)).some)
    case (Left(f), _) => Monad[F].pure(Some(Left(f)))
    case _            => Monad[F].pure(None)
  }

  private def getAsnLookup(
    ipAddress: Either[Throwable, InetAddress],
    ispResponse: Option[Either[Throwable, Isp]]
  ): F[Option[Either[Throwable, Asn]]] = {
    val asnFromIsp = ispResponse.map(_.flatMap(_.asn))
    asnFromIsp.flatMap(_.toOption) match {
      case Some(_) => Monad[F].pure(asnFromIsp)
      case None =>
        (ipAddress, asnService) match {
          case (Right(ipA), Some(gs)) =>
            SR.getValue(ReaderFunctions.asn, gs, ipA)
              .map(asnResponse => asnResponse.flatMap(Asn.create).some)
          case (Left(f), _) => Monad[F].pure(Some(Left(f)))
          case _            => Monad[F].pure(asnFromIsp)
        }
    }
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
      ipAddress   <- IAR.resolve(ip)
      ipLocation  <- getLocationLookup(ipAddress)
      ispResponse <- getIspLookup(ipAddress)
      ispName = ispResponse.map(_.map(_.name))
      org     = ispResponse.map(_.map(_.organization))
      asn            <- getAsnLookup(ipAddress, ispResponse)
      domain         <- getLookup(ipAddress, domainService)
      connectionType <- getLookup(ipAddress, connectionTypeService)
      anonymous      <- getAnonymousIpLookup(ipAddress)
    } yield IpLookupResult(ipLocation, ispName, org, asn, domain, connectionType, anonymous)

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
    val lookupAndCache =
      performLookupsWithoutLruCache(ip).flatMap { result =>
        lru.put(ip, result).map(_ => result)
      }

    lru
      .get(ip)
      .map(_.map(Monad[F].pure(_)))
      .flatMap(_.getOrElse(lookupAndCache))
  }
}
