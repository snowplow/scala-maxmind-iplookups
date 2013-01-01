/* 
 * Copyright (c) 2012 SnowPlow Analytics Ltd. All rights reserved.
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

object IpGeoTest {

  type DataGrid = scala.collection.immutable.Map[String, Option[IpLocation]]

  val GeoLiteCity: IpGeo = {
    val dbFilepath = getClass.getResource("/maxmind/GeoLiteCity.dat").toURI()
    new IpGeo(dbFile = new File(dbFilepath), fromDisk = false)
  }

  val testData: DataGrid = Map(
    "213.52.50.8" -> // Norwegian IP address, provided by MaxMind in their test suite
    Some(IpLocation(
      countryCode = "NO",
      countryName = "Norway",
      region = Some("01"),
      city = Some("Ås"),
      latitude = 59.666702F,
      longitude = 10.800003F,
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None
    )),

    "128.232.0.0" -> // Cambridge uni address, taken from http://www.ucs.cam.ac.uk/network/ip/camnets.html
    Some(IpLocation(
      countryCode = "GB",
      countryName = "United Kingdom",
      region = None,
      city = None,
      latitude = 54.0F,
      longitude = -2.0F,
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None
    )),

    "192.197.77.0" -> // Canadian government, taken from http://en.wikipedia.org/wiki/Wikipedia:Blocking_IP_addresses
    Some(IpLocation(
      countryCode = "CA",
      countryName = "Canada",
      region = Some("ON"),
      city = Some("Ottawa"),
      latitude = 45.416702F,
      longitude = -75.7F,
      postalCode = None,
      dmaCode = None,
      areaCode = None,
      metroCode = None
    )),

    "192.0.2.0" -> // Invalid IP address, as per http://stackoverflow.com/questions/10456044/what-is-a-good-invalid-ip-address-to-use-for-unit-tests
    None
  )
}

class IpGeoTest extends Specification {

  "Looking up some IP address locations should match their expected locations" >> {

    import IpGeoTest._
    testData foreach { case (ip, expected) =>

      "The IP address %s".format(ip) should {

        val actual = GeoLiteCity.getLocation(ip)

        if (expected == None) {
          "not be found" in {
            actual must beNone
          }
        } else {

          val a = actual.get
          val e = expected.get

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
        }
      }
    }
  }
}