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

import cats.effect.IO
import cats.effect.testing.specs2.CatsEffect
import cats.implicits._
import cats.{Id, Monad}
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.snowplowanalytics.maxmind.iplookups.IpLookupsTest.ipLookupsFromFiles
import com.snowplowanalytics.maxmind.iplookups.model._
import org.specs2.mutable.Specification
import org.specs2.specification.Tables

import java.net.UnknownHostException

object IpLookupsTest {
  val geoFile            = getClass.getResource("GeoIP2-City-Test.mmdb").getFile
  val ispFile            = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
  val domainFile         = getClass.getResource("GeoIP2-Domain-Test.mmdb").getFile
  val connectionTypeFile = getClass.getResource("GeoIP2-Connection-Type-Test.mmdb").getFile
  val anonymousFile      = getClass.getResource("GeoIP2-Anonymous-IP-Test.mmdb").getFile

  def ipLookupsFromFiles[F[_]: CreateIpLookups](memCache: Boolean, lruCache: Int): F[IpLookups[F]] =
    CreateIpLookups[F]
      .createFromFilenames(
        Some(geoFile),
        Some(ispFile),
        Some(domainFile),
        Some(connectionTypeFile),
        Some(anonymousFile),
        memCache,
        lruCache
      )

  def failedLookupCauseUnknownHost(host: String): IpLookupResult = IpLookupResult(
    ipLocation = unknownHostException(host),
    isp = unknownHostException(host),
    organization = unknownHostException(host),
    domain = unknownHostException(host),
    connectionType = unknownHostException(host),
    anonymousIp = unknownHostException(host)
  )

  private def unknownHostException(host: String) = new UnknownHostException(host).asLeft.some

  // Databases and test data taken from https://github.com/maxmind/MaxMind-DB/tree/master/test-data
  val testData: Map[String, IpLookupResult] = Map(
    "175.16.199.0" -> IpLookupResult(
      IpLocation(
        countryCode = "CN",
        countryName = "China",
        region = Some("22"),
        city = Some("Changchun"),
        latitude = 43.88f,
        longitude = 125.3228f,
        timezone = Some("Asia/Harbin"),
        postalCode = None,
        metroCode = None,
        regionName = Some("Jilin Sheng"),
        isInEuropeanUnion = false,
        continent = "Asia",
        accuracyRadius = 100
      ).asRight.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      "Dialup".asRight.some,
      AnonymousIp(
        ipAddress = "175.16.199.0",
        isAnonymous = false,
        isAnonymousVpn = false,
        isHostingProvider = false,
        isPublicProxy = false,
        isTorExitNode = false
      ).asRight.some
    ),
    "216.160.83.56" -> IpLookupResult(
      IpLocation(
        countryCode = "US",
        countryName = "United States",
        region = Some("WA"),
        city = Some("Milton"),
        latitude = 47.2513f,
        longitude = -122.3149f,
        timezone = Some("America/Los_Angeles"),
        postalCode = Some("98354"),
        metroCode = Some(819),
        regionName = Some("Washington"),
        isInEuropeanUnion = false,
        continent = "North America",
        accuracyRadius = 22
      ).asRight.some,
      "Century Link".asRight.some,
      "Lariat Software".asRight.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").asLeft.some,
      AnonymousIp(
        ipAddress = "216.160.83.56",
        isAnonymous = false,
        isAnonymousVpn = false,
        isHostingProvider = false,
        isPublicProxy = false,
        isTorExitNode = false
      ).asRight.some
    ),
    "67.43.156.0" -> IpLookupResult(
      IpLocation(
        countryCode = "BT",
        countryName = "Bhutan",
        region = None,
        city = None,
        latitude = 27.5f,
        longitude = 90.5f,
        timezone = Some("Asia/Thimphu"),
        postalCode = None,
        metroCode = None,
        regionName = None,
        isInEuropeanUnion = false,
        continent = "Asia",
        accuracyRadius = 534
      ).asRight.some,
      "Loud Packet".asRight.some,
      "zudoarichikito_".asRight.some,
      "shoesfin.NET".asRight.some,
      new AddressNotFoundException("The address 67.43.156.0 is not in the database.").asLeft.some,
      AnonymousIp(
        ipAddress = "67.43.156.0",
        isAnonymous = false,
        isAnonymousVpn = false,
        isHostingProvider = false,
        isPublicProxy = false,
        isTorExitNode = false
      ).asRight.some
    ),
    "81.2.69.11" -> IpLookupResult(
      new AddressNotFoundException("The address 81.2.69.11 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 81.2.69.11 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 81.2.69.11 is not in the database.").asLeft.some,
      "in-addr.arpa".asRight.some,
      new AddressNotFoundException("The address 81.2.69.11 is not in the database.").asLeft.some,
      AnonymousIp(
        ipAddress = "81.2.69.11",
        isAnonymous = true,
        isAnonymousVpn = true,
        isHostingProvider = true,
        isPublicProxy = true,
        isTorExitNode = true
      ).asRight.some
    ),
    // Invalid IP address, as per
    // http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
    "192.0.2.0" -> IpLookupResult(
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some
    )
  )
}

class IpLookupsTest extends Specification with Tables with CatsEffect {

  "Looking up some IP address locations should match their expected locations" should {
    import IpLookupsTest._

    for {
      memCache <- Seq(true, false)
      lruCache <- Seq(0, 1000, 10000)
    } {
      testData foreach { case (ip, expected) =>
        prepareTestFormatter(ip, memCache, lruCache) should {
          "work for IO monad" in {
            assertWithFiles[IO](memCache, lruCache, ip, expected)
          }
          "work for Id monad" in {
            assertWithFiles[Id](memCache, lruCache, ip, expected)
          }
        }
      }
    }

    "providing an invalid ip" should {
      "fail with 'UnknownHostException' for IO monad" in {
        val expected = failedLookupCauseUnknownHost("not: Name or service not known")

        assertWithFiles[IO](memCache = true, lruCache = 0, ip = "not", expected)
      }
      "fail with 'UnknownHostException' for Id monad" in {
        val expected = failedLookupCauseUnknownHost("not")

        assertWithFiles[Id](memCache = true, lruCache = 0, ip = "not", expected)
      }
    }

    "providing no files" should {
      "return Nones for IO monad" in {
        assertNoneWithoutFiles[IO]
      }
      "return Nones for Id monad" in {
        assertNoneWithoutFiles[Id]
      }
    }
  }

  private def prepareTestFormatter: (String, Boolean, Int) => String = {
    val mcf: Boolean => String = mc => if (mc) "using" else "without using"
    val lcf: Int => String =
      lc => if (lc > 0) "LRU cache sized %s".format(lc) else "no LRU cache"
    (ip, mcache, lcache) =>
      "The IP address %s looked up (%s memory cache and with %s)".format(
        ip,
        mcf(mcache),
        lcf(lcache)
      )
  }

  private def assertWithFiles[F[_]: CreateIpLookups: Monad](
    memCache: Boolean,
    lruCache: Int,
    ip: String,
    expected: IpLookupResult
  ) = {
    ipLookupsFromFiles[F](memCache, lruCache)
      .flatMap(_.performLookups(ip))
      .map(r => matchIpLookupResult(r, expected))
  }

  private def assertNoneWithoutFiles[F[_]: CreateIpLookups: Monad] = {
    val noFilesLookup = CreateIpLookups[F].createFromFiles(
      None,
      None,
      None,
      None,
      None,
      memCache = true,
      lruCacheSize = 0
    )
    val expected = IpLookupResult(None, None, None, None, None, None)

    noFilesLookup
      .flatMap(_.performLookups("67.43.156.0"))
      .map(r => matchIpLookupResult(r, expected))
  }

  private def matchIpLookupResult(actual: IpLookupResult, expected: IpLookupResult) = {
    "field" | "expected" | "actual" |>
      "iplocation" ! expected.ipLocation ! actual.ipLocation |
      "isp" ! expected.isp ! actual.isp |
      "organization" ! expected.organization ! actual.organization |
      "domain" ! expected.domain ! actual.domain |
      "connection type" ! expected.connectionType ! actual.connectionType |
      "anonymous" ! expected.anonymousIp ! actual.anonymousIp | { (_, e, a) =>
        matchThrowables(e, a)
      }
  }

  // Match on Throwable's ClassName instead of error message, because messages are os-specific
  private def matchThrowables[A](
    actual: Option[Either[Throwable, A]],
    expected: Option[Either[Throwable, A]]
  ): Boolean =
    actual.map(_.leftMap(_.getClass)) must_== expected.map(_.leftMap(_.getClass))
}
