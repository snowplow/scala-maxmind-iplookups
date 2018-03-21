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

import java.net.UnknownHostException

import com.maxmind.geoip2.exception.AddressNotFoundException
import org.specs2.mutable.Specification
import scalaz._
import Scalaz._

object IpLookupsTest {

  def ipLookupsFromFiles(memCache: Boolean, lruCache: Int): IpLookups = {
    val geoFile    = getClass.getResource("GeoIP2-City-Test.mmdb").getFile
    val ispFile    = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
    val orgFile    = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
    val domainFile = getClass.getResource("GeoIP2-Domain-Test.mmdb").getFile
    val netspeedFile = getClass.getResource("GeoIP2-Connection-Type-Test.mmdb").getFile

    IpLookups(Some(geoFile), Some(ispFile), Some(orgFile), Some(domainFile), Some(netspeedFile), memCache, lruCache)
  }

  val testData: Map[String, IpLookupResult] = Map(
    // Databases and test data taken from https://github.com/maxmind/MaxMind-DB/tree/master/test-data
    "175.16.199.0" ->
      (IpLocation(
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
      ).success.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 175.16.199.0 is not in the database.").failureNel.some,
      "Dialup".success.some),

    "216.160.83.56" ->
      (IpLocation(
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
      ).success.some,
      "Century Link".success.some,
      "Lariat Software".success.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 216.160.83.56 is not in the database.").failureNel.some),

    "67.43.156.0" ->
      (IpLocation(
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
      ).success.some,
      "Loud Packet".success.some,
      "zudoarichikito_".success.some,
      "shoesfin.NET".success.some,
      new AddressNotFoundException("The address 67.43.156.0 is not in the database.").failureNel.some),

    "192.0.2.0" -> // Invalid IP address, as per http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
      (new AddressNotFoundException("The address 192.0.2.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").failureNel.some,
      new AddressNotFoundException("The address 192.0.2.0 is not in the database.").failureNel.some)
  )
}

class IpLookupsTest extends Specification {

  "Looking up some IP address locations should match their expected locations" should {

    val mcf: Boolean => String = mc => if (mc) "using" else "without using"
    val lcf: Int => String = lc => if (lc > 0) "LRU cache sized %s".format(lc) else "no LRU cache"
    val formatter: (String, Boolean, Int) => String =
      (ip, mcache, lcache) => "The IP address %s looked up (%s memory cache and with %s)".format(ip, mcf(mcache), lcf(lcache))

    import IpLookupsTest._
    for (memCache <- Seq(true, false);
         lruCache <- Seq(0, 1000, 10000)) {

      val ipLookups = ipLookupsFromFiles(memCache, lruCache)

      testData foreach { case (ip, expected) =>
        formatter(ip, memCache, lruCache) should {
          val actual = ipLookups.performLookups(ip)
          matchIpLookupResult(actual, expected)
        }
      }
    }

    "providing an invalid ip should fail" in {
      val ipLookups = ipLookupsFromFiles(true, 0)
      val expected =
        (new UnknownHostException("not: Name or service not known").failureNel.some,
        new UnknownHostException("not: Name or service not known").failureNel.some,
        new UnknownHostException("not: Name or service not known").failureNel.some,
        new UnknownHostException("not: Name or service not known").failureNel.some,
        new UnknownHostException("not: Name or service not known").failureNel.some)
      val actual = ipLookups.performLookups("not")
      matchIpLookupResult(actual, expected)
    }

    "providing no files should return Nones" in {
      val ipLookups = IpLookups(None, None, None, None, None, true, 0)
      val expected = (None, None, None, None, None)
      val actual = ipLookups.performLookups("67.43.156.0")
      matchIpLookupResult(actual, expected)
    }
  }

  private def matchIpLookupResult(actual: IpLookupResult, expected: IpLookupResult) = {
    s"have iplocation = ${actual._1}" in { matchThrowables(actual._1, expected._1) }
    s"have isp = ${actual._2}" in { matchThrowables(actual._2, expected._2) }
    s"have org = ${actual._3}" in { matchThrowables(actual._3, expected._3) }
    s"have domain = ${actual._4}" in { matchThrowables(actual._4, expected._4) }
    s"have net speed = ${actual._5}" in { matchThrowables(actual._5, expected._5) }
  }

  // needed because == doesn't work on exceptions
  private def matchThrowables[A](
    actual: Option[ValidationNel[Throwable, A]],
    expected: Option[ValidationNel[Throwable, A]]
  ): Boolean = actual match {
    case None => actual must_== expected
    case Some(r) => r match {
      case Success(_) => actual must_== expected
      case Failure(_) => getErrorMessagesNel(actual) must_== getErrorMessagesNel(expected)
    }
  }

  private def getErrorMessagesNel[A](
      e: Option[ValidationNel[Throwable, A]]): Option[ValidationNel[String, A]] =
    e.map(_.leftMap(_.map(_.getMessage)))
}
