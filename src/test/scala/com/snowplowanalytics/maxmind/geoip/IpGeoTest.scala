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
package com.snowplowanalytics.maxmind.geoip

// Java
import java.io.File

// Specs2
import org.specs2.mutable.Specification

// TODO: look into Specs2 DataTables

object IpLookupsTest {

  type DataGrid = scala.collection.immutable.Map[String, IpLookupResult]

  def GeoLiteCity(memCache: Boolean, lruCache: Int): IpLookups = {
    val geoFilepath    = "./src/test/resources/maxmind/GeoIPCity.dat"
    val ispFilepath    = "./src/test/resources/maxmind/GeoIPISP.dat"
    val orgFilepath    = "./src/test/resources/maxmind/GeoIPOrg.dat"
    val domainFilepath = "./src/test/resources/maxmind/GeoIPDomain.dat"
    
    IpLookups(geoFilepath, memCache, lruCache, Some(ispFilepath), Some(orgFilepath), Some(domainFilepath))
  }

  val testData: DataGrid = Map(

    "70.46.123.145" -> // ISP, organization, and domain lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/java/com/maxmind/geoip/OrgLookupTest.java
    (Some(IpLocation(
      countryCode = "US",
      countryName = "United States",
      region = Some("FL"),
      city = Some("Delray Beach"),
      latitude = 26.461502F,
      longitude = -80.0728F,
      postalCode = None,
      dmaCode = Some(548),
      areaCode = Some(561),
      metroCode = Some(548),
      regionName = Some("Florida")
    )), Some("FDN Communications"), Some("DSLAM WAN Allocation"), Some("nuvox.net")),

    "89.92.213.32" -> // ISP, organization, and domain lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/resources/GeoIP/GeoIP.csv
    (Some(IpLocation(
      countryCode = "FR",
      countryName = "France",
      region = Some("B4"),
      city = Some("Lille"),
      latitude = 50.632996F,
      longitude = 3.0585938F,
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None,
      regionName = Some("Nord-Pas-de-Calais")
    )), Some("Bouygues Telecom"), Some("Bouygues Telecom"), Some("bbox.fr")),

    "67.43.156.0" -> // ISP, organization, and domain lookup example from https://github.com/maxmind/geoip-api-java/blob/892ad0f8d49dc4eeeec6fece1309d6ff620c7737/src/test/java/com/maxmind/geoip/DomainLookupTest.java
    (Some(IpLocation(
      countryCode = "A1",
      countryName = "Anonymous Proxy",
      region = None,
      city = None,
      latitude = 0.0F,
      longitude = 0.0F,
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None,
      regionName = None
    )), Some("Loud Packet"), Some("zudoarichikito_"), Some("shoesfin.NET")),

    "192.0.2.0" -> // Invalid IP address, as per http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
    (None, None, None, None)
  )
}

class IpLookupsTest extends Specification {

  "Looking up some IP address locations should match their expected locations" >> {

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

          val actual = ipLookups.getLocation(ip)

          expected match {
            case (None, None, None, None) =>
              "not be found" in {
                actual must_== (None, None, None, None)
              }

            case (Some(e), isp, org, domain) =>
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

            case _ => throw new Exception("Expected lookup result could not be matched - this should never happen")
          }
        }
      }
    }
  }
}
