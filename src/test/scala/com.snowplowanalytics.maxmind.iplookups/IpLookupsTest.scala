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

// Scala
import scala.io.Source

// Specs2
import org.specs2.mutable.Specification

object IpLookupsTest {

  type DataGrid = scala.collection.immutable.Map[String, IpLookupResult]

  def GeoLiteCity(memCache: Boolean, lruCache: Int): IpLookups = {
    val geoFile    = getClass.getResource("GeoIP2-City-Test.mmdb").getFile
    val ispFile    = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
    val orgFile    = getClass.getResource("GeoIP2-ISP-Test.mmdb").getFile
    val domainFile = getClass.getResource("GeoIP2-Domain-Test.mmdb").getFile
    val netspeedFile = getClass.getResource("GeoIP2-Connection-Type-Test.mmdb").getFile
  
    IpLookups(Some(geoFile), Some(ispFile), Some(orgFile), Some(domainFile), Some(netspeedFile), memCache, lruCache)
  }

  // TODO: replace with Specs2 DataTables, https://github.com/snowplow/scala-maxmind-geoip/issues/17
  val testData: DataGrid = Map(

    // Databases and test data taken from https://github.com/maxmind/MaxMind-DB/tree/master/test-data
    "175.16.199.0" ->
    (Some(IpLocation(
      countryCode = "CN",
      countryName = "China",
      region = Some("22"),
      city = Some("Changchun"),
      latitude = 43.88F,
      longitude = 125.3228F,
      timezone = Some("Asia/Harbin"),
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None,
      regionName = Some("Jilin Sheng")
    )), None, None, None, Some("Dialup")),

    "216.160.83.56" ->
    (Some(IpLocation(
      countryCode = "US",
      countryName = "United States",
      region = Some("WA"),
      city = Some("Milton"),
      latitude = 47.2513F,
      longitude = -122.3149F,
      timezone = Some("America/Los_Angeles"),
      postalCode = Some("98354"),
      dmaCode = None,
      areaCode = None,
      metroCode = Some(819),
      regionName = Some("Washington")
    )), Some("Century Link"), Some("Lariat Software"), None, None),

    "67.43.156.0" ->
    (Some(IpLocation(
      countryCode = "BT",
      countryName = "Bhutan",
      region = None,
      city = None,
      latitude = 27.5F,
      longitude = 90.5F,
      timezone = Some("Asia/Thimphu"),
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None,
      regionName = None
    )), Some("Loud Packet"), Some("zudoarichikito_"), Some("shoesfin.NET"), None),

    "192.0.2.0" -> // Invalid IP address, as per http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
    (None, None, None, None, None)
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

      val ipLookups = GeoLiteCity(memCache, lruCache)

      testData foreach { case (ip, expected) =>

        formatter(ip, memCache, lruCache) should {

          val actual = ipLookups.performLookups(ip)

          expected match {
            case (None, None, None, None, None) =>
              "not be found" in {
                actual must_== (None, None, None, None, None)
              }

            case (Some(e), isp, org, domain, netspeed) =>
              "not be None" in {
                actual._1 must not beNone
              }

              val a = actual._1.getOrElse(throw new Exception("Geo lookup failed"))
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
                actual._2 must_== isp
              }
              "have org = %s".format(org) in {
                actual._3 must_== org
              }                      
              "have domain = %s".format(domain) in {
                actual._4 must_== domain
              }      
              "have net speed = %s".format(netspeed) in {
                actual._5 must_== netspeed
              }     
            case _ => throw new Exception("Expected lookup result could not be matched - this should never happen")
          }
        }
      }
    }
  }
}
