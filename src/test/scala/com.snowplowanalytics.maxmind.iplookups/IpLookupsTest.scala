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

import com.maxmind.geoip2.exception.AddressNotFoundException
import java.net.UnknownHostException
import org.specs2.mutable.Specification
import cats.syntax.either._
import cats.syntax.option._

import model._

object IpLookupsTest {

  def ipLookupsFromFiles(memCache: Boolean, lruCache: Int): IpLookups = {
    val geoFile            = getClass.getResource("GeoIP2-City-Test.mmdb").getFile
    val ispFile            = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
    val domainFile         = getClass.getResource("GeoIP2-Domain-Test.mmdb").getFile
    val connectionTypeFile = getClass.getResource("GeoIP2-Connection-Type-Test.mmdb").getFile

    IpLookups(
      Some(geoFile),
      Some(ispFile),
      Some(domainFile),
      Some(connectionTypeFile),
      memCache,
      lruCache)
  }

  // Databases and test data taken from https://github.com/maxmind/MaxMind-DB/tree/master/test-data
  val testData: Map[String, IpLookupResult] = Map(
    "175.16.199.0" -> IpLookupResult(
      IpLocation(
        countryCode = "CN",
        countryName = "China",
        region = Some("22"),
        city = Some("Changchun"),
        latitude = 43.88F,
        longitude = 125.3228F,
        timezone = Some("Asia/Harbin"),
        postalCode = None,
        metroCode = None,
        regionName = Some("Jilin Sheng")
      ).asRight.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").asLeft.some,
      "Dialup".asRight.some
    ),
    "216.160.83.56" -> IpLookupResult(
      IpLocation(
        countryCode = "US",
        countryName = "United States",
        region = Some("WA"),
        city = Some("Milton"),
        latitude = 47.2513F,
        longitude = -122.3149F,
        timezone = Some("America/Los_Angeles"),
        postalCode = Some("98354"),
        metroCode = Some(819),
        regionName = Some("Washington")
      ).asRight.some,
      "Century Link".asRight.some,
      "Lariat Software".asRight.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").asLeft.some
    ),
    "67.43.156.0" -> IpLookupResult(
      IpLocation(
        countryCode = "BT",
        countryName = "Bhutan",
        region = None,
        city = None,
        latitude = 27.5F,
        longitude = 90.5F,
        timezone = Some("Asia/Thimphu"),
        postalCode = None,
        metroCode = None,
        regionName = None
      ).asRight.some,
      "Loud Packet".asRight.some,
      "zudoarichikito_".asRight.some,
      "shoesfin.NET".asRight.some,
      new AddressNotFoundException("The address 67.43.156.0 is not in the database.").asLeft.some
    ),
    // Invalid IP address, as per
    // http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
    "192.0.2.0" -> IpLookupResult(
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").asLeft.some
    )
  )
}

class IpLookupsTest extends Specification {

  "Looking up some IP address locations should match their expected locations" should {

    val mcf: Boolean => String = mc => if (mc) "using" else "without using"
    val lcf: Int => String =
      lc => if (lc > 0) "LRU cache sized %s".format(lc) else "no LRU cache"
    val formatter: (String, Boolean, Int) => String =
      (ip, mcache, lcache) =>
        "The IP address %s looked up (%s memory cache and with %s)".format(
          ip,
          mcf(mcache),
          lcf(lcache))

    import IpLookupsTest._

    for {
      memCache <- Seq(true, false)
      lruCache <- Seq(0, 1000, 10000)
    } {

      val ipLookups = ipLookupsFromFiles(memCache, lruCache)

      testData foreach {
        case (ip, expected) =>
          formatter(ip, memCache, lruCache) should {
            val actual = ipLookups.performLookups(ip)
            matchIpLookupResult(actual, expected)
          }
      }
    }

    "providing an invalid ip should fail" in {
      val ipLookups = ipLookupsFromFiles(true, 0)
      val expected = IpLookupResult(
        new UnknownHostException("not: Name or service not known").asLeft.some,
        new UnknownHostException("not: Name or service not known").asLeft.some,
        new UnknownHostException("not: Name or service not known").asLeft.some,
        new UnknownHostException("not: Name or service not known").asLeft.some,
        new UnknownHostException("not: Name or service not known").asLeft.some
      )
      val actual = ipLookups.performLookups("not")
      matchIpLookupResult(actual, expected)
    }

    "providing no files should return Nones" in {
      val ipLookups = IpLookups(None, None, None, None, true, 0)
      val expected  = IpLookupResult(None, None, None, None, None)
      val actual    = ipLookups.performLookups("67.43.156.0")
      matchIpLookupResult(actual, expected)
    }
  }

  private def matchIpLookupResult(actual: IpLookupResult, expected: IpLookupResult) = {
    s"have iplocation = ${actual.ipLocation}" in {
      matchThrowables(actual.ipLocation, expected.ipLocation)
    }
    s"have isp = ${actual.isp}" in { matchThrowables(actual.isp, expected.isp) }
    s"have org = ${actual.organization}" in {
      matchThrowables(actual.organization, expected.organization)
    }
    s"have domain = ${actual.domain}" in { matchThrowables(actual.domain, expected.domain) }
    s"have net speed = ${actual.connectionType}" in {
      matchThrowables(actual.connectionType, expected.connectionType)
    }
  }

  // needed because == doesn't work on exceptions
  private def matchThrowables[A](
    actual: Option[Either[Throwable, A]],
    expected: Option[Either[Throwable, A]]
  ): Boolean = actual match {
    case None => actual must_== expected
    case Some(r) =>
      r match {
        case Right(_) => actual must_== expected
        case Left(_)  => getErrorMessage(actual) must_== getErrorMessage(expected)
      }
  }

  private def getErrorMessage[A](e: Option[Either[Throwable, A]]): Option[Either[String, A]] =
    e.map(_.leftMap(_.getMessage))
}
