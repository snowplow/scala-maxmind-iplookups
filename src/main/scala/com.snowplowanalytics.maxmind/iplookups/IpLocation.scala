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

// MaxMind
import com.maxmind.geoip2.model.CityResponse

/**
 * A case class wrapper around the
 * MaxMind Location class.
 */
case class IpLocation(
  countryCode: String,
  countryName: String,
  region: Option[String],
  city: Option[String],
  latitude: Float,
  longitude: Float,
  timezone: Option[String],
  postalCode: Option[String],
  dmaCode: Option[Int],
  areaCode: Option[Int],
  metroCode: Option[Int],
  regionName: Option[String]  
  )

/**
 * Companion class contains a constructor
 * which takes a MaxMind Location class.
 */
object IpLocation {
  /**
   * Constructs an IpLocation instance
   * from a MaxMind Location instance
   * 
   * @param loc MaxMind Location object
   * @return IpLocation
   */
  def apply(loc: CityResponse): IpLocation =
    IpLocation(
      countryCode = loc.getCountry.getIsoCode,
      countryName = loc.getCountry.getName,
      region = Option(loc.getMostSpecificSubdivision.getIsoCode),
      city = Option(loc.getCity.getName),
      latitude = Option(loc.getLocation.getLatitude).map(_.toFloat).getOrElse(0F),
      longitude = Option(loc.getLocation.getLongitude).map(_.toFloat).getOrElse(0F),
      timezone =  Option(loc.getLocation.getTimeZone),
      postalCode = Option(loc.getPostal.getCode),
      dmaCode = None,
      areaCode = None,
      metroCode = Option(loc.getLocation.getMetroCode).map(_.toInt),
      regionName = Option(loc.getMostSpecificSubdivision.getName)
    )
}
