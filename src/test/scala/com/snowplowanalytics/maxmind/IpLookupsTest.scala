/* Copyright (c) 2012-2013 Snowplow Analytics Ltd. and Micronautics Research Corporation. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under. */

package com.snowplowanalytics.maxmind

import org.specs2.mutable.Specification
import org.specs2.specification.core.{Fragment, Fragments}
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

object IpLookupsTest {
  type DataGrid = scala.collection.immutable.Map[String, IpLookupResult]

  def GeoLiteCity(memCache: Boolean, lruCache: Int): IpLookups = {
    val geoFile      = getClass.getResource("GeoIPCity.dat").getFile
    val ispFile      = getClass.getResource("GeoIPISP.dat").getFile
    val orgFile      = getClass.getResource("GeoIPOrg.dat").getFile
    val domainFile   = getClass.getResource("GeoIPDomain.dat").getFile
    val netSpeedFile = getClass.getResource("GeoIPNetSpeedCell.dat").getFile

    IpLookups(Some(geoFile), Some(ispFile), Some(orgFile), Some(domainFile), Some(netSpeedFile), memCache, lruCache)
  }

  // TODO: replace with Specs2 DataTables, https://github.com/snowplow/scala-maxmind-geoip/issues/17
  val testData: DataGrid = Map(
    "70.46.123.145" -> // ISP, organization, and domain, and net speed lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/java/com/maxmind/geoip/OrgLookupTest.java
      IpLookupResult(
        Some(IpLocation(
        countryCode = "US",
        countryName = "United States",
        region = Some("FL"),
        city = Some("Delray Beach"),
        latitude = 26.461502F,
        longitude = -80.0728F,
        timezone = Some("America/New_York"),
        postalCode = None,
        dmaCode = Some(548),
        areaCode = Some(561),
        metroCode = Some(548),
        regionName = Some("Florida")
      )),
      Some("FDN Communications"),
      Some("DSLAM WAN Allocation"),
      Some("nuvox.net"),
      Some("Cable/DSL")
    ),

    "89.92.213.32" -> // ISP, organization, and domain, and net speed lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/resources/GeoIP/GeoIP.csv
      IpLookupResult(
        Some(IpLocation(
          countryCode = "FR",
          countryName = "France",
          region = Some("B4"),
          city = Some("Lille"),
          latitude = 50.632996F,
          longitude = 3.0585938F,
          timezone = Some("Europe/Paris"),
          postalCode = None,
          dmaCode = None,
          areaCode = None,
          metroCode = None,
          regionName = Some("Nord-Pas-de-Calais")
        )),
        Some("Bouygues Telecom"),
        Some("Bouygues Telecom"),
        Some("bbox.fr"),
        Some("Cable/DSL")
      ),

    "67.43.156.0" -> // ISP, organization, and domain, and net speed lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/java/com/maxmind/geoip/DomainLookupTest.java
      IpLookupResult(
        Some(IpLocation(
          countryCode = "A1",
          countryName = "Anonymous Proxy",
          region = None,
          city = None,
          latitude = 0.0F,
          longitude = 0.0F,
          timezone = None,
          postalCode = None,
          dmaCode = None,
          areaCode = None,
          metroCode = None,
          regionName = None
        )),
        Some("Loud Packet"),
        Some("zudoarichikito_"),
        Some("shoesfin.NET"),
        Some("Corporate")
      ),

    "192.0.2.0" -> // Invalid IP address, as per http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
      IpLookupResult(None, None, None, None, None)
  )
}

class IpLookupsTest extends Specification {
  "Looking up some IP address locations should match their expected locations" should {
    val mcf: Boolean => String = mc => if (mc) "using" else "without using"
    val lcf: Int => String = lc => if (lc > 0) "LRU cache sized %s".format(lc) else "no LRU cache"
    val formatter = (ip: String, mCache: Boolean, lCache: Int) =>
      s"The IP address $ip looked up (${ mcf(mCache) } memory cache and with ${ lcf(lCache) })"

    import com.snowplowanalytics.maxmind.IpLookupsTest._
    val tests = for {
      memCache <- Seq(true, false)
      lruCache <- Seq(0, 1000, 10000)
    } yield {
      val ipLookups = GeoLiteCity(memCache, lruCache)
      testData.toSeq map { case (ip, expected) =>
        formatter(ip, memCache, lruCache) should {
          val actual = ipLookups.performLookups(ip)
          expected match {
            case IpLookupResult(None, _, _, _, _) =>
              "not be found" in {
                actual must_== IpLookupResult()
              }

            case IpLookupResult(Some(e), isp, org, domain, netSpeed) =>
              "not be None" in {
                actual.maybeIpLocation must not beNone
              }

              val a = actual.maybeIpLocation.getOrElse(throw new Exception("Geo lookup failed"))
              "have countryCode = %s".format(e.countryCode) in {
                a.countryCode must_== e.countryCode
              }
              "have countryName = %s".format(e.countryName) in {
                a.countryName must_== e.countryName
              }
              "have region = %s".format(e.region) in {
                a.region must_== e.region
              }
              "have city = %s".format(e.city) in {
                a.city must_== e.city
              }
              "have latitude = %s".format(e.latitude) in {
                a.latitude must_== e.latitude
              }
              "have longitude = %s".format(e.longitude) in {
                a.longitude must_== e.longitude
              }
              "have timezone = %s".format(e.timezone) in {
                a.timezone must_== e.timezone
              }
              "have postalCode = %s".format(e.postalCode) in {
                a.postalCode must_== e.postalCode
              }
              "have dmaCode = %s".format(e.dmaCode) in {
                a.dmaCode must_== e.dmaCode
              }
              "have areaCode = %s".format(e.areaCode) in {
                a.areaCode must_== e.areaCode
              }
              "have metroCode = %s".format(e.metroCode) in {
                a.metroCode must_== e.metroCode
              }
              "have regionName = %s".format(e.regionName) in {
                a.regionName must_== e.regionName
              }
              "have isp = %s".format(isp) in {
                actual.maybeIsp must_== isp
              }
              "have org = %s".format(org) in {
                actual.maybeOrg must_== org
              }
              "have domain = %s".format(domain) in {
                actual.maybeDomain must_== domain
              }
              "have net speed = %s".format(netSpeed) in {
                actual.maybeNetSpeed must_== netSpeed
              }
            case _ => throw new Exception("Expected lookup result could not be matched - this should never happen")
          }
        }
      }
    }
    toSpecs2ResultFragments(tests)
  }

  private def toSpecs2ResultFragments(tests: Seq[Seq[Fragment]]): Fragments = {
    val testsFlatten: Seq[Fragment] = tests.flatten
    val initialFragments: Fragments = fragmentToFragments(testsFlatten.head)
    (testsFlatten foldLeft initialFragments)(_ ^ _)
  }
}
