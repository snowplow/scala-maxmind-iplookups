/*
 * Copyright (c) 2012-2019 Snowplow Analytics Ltd. All rights reserved.
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

import java.net.InetAddress

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse

object model {
  type ReaderFunction = (DatabaseReader, InetAddress) => String

  /** A case class wrapper around the MaxMind CityResponse class. */
  final case class IpLocation(
    countryCode: String,
    countryName: String,
    region: Option[String],
    city: Option[String],
    latitude: Float,
    longitude: Float,
    timezone: Option[String],
    postalCode: Option[String],
    metroCode: Option[Int],
    regionName: Option[String],
    isInEuropeanUnion: Boolean,
    continent: String,
    accuracyRadius: Int
  )

  /** Companion class contains a constructor which takes a MaxMind CityResponse. */
  object IpLocation {

    /**
     * Constructs an IpLocation instance from a MaxMind CityResponse instance.
     * @param cityResponse MaxMind CityResponse object
     * @return IpLocation
     */
    def apply(cityResponse: CityResponse): IpLocation =
      IpLocation(
        countryCode = cityResponse.getCountry.getIsoCode,
        countryName = cityResponse.getCountry.getName,
        region = Option(cityResponse.getMostSpecificSubdivision.getIsoCode),
        city = Option(cityResponse.getCity.getName),
        latitude = Option(cityResponse.getLocation.getLatitude).map(_.toFloat).getOrElse(0F),
        longitude = Option(cityResponse.getLocation.getLongitude).map(_.toFloat).getOrElse(0F),
        timezone = Option(cityResponse.getLocation.getTimeZone),
        postalCode = Option(cityResponse.getPostal.getCode),
        metroCode = Option(cityResponse.getLocation.getMetroCode).map(_.toInt),
        regionName = Option(cityResponse.getMostSpecificSubdivision.getName),
        isInEuropeanUnion = cityResponse.getCountry.isInEuropeanUnion,
        continent = cityResponse.getContinent.getName,
        accuracyRadius = cityResponse.getLocation.getAccuracyRadius
      )
  }

  /** Result of MaxMind lookups */
  final case class IpLookupResult(
    ipLocation: Option[Either[Throwable, IpLocation]],
    isp: Option[Either[Throwable, String]],
    organization: Option[Either[Throwable, String]],
    domain: Option[Either[Throwable, String]],
    connectionType: Option[Either[Throwable, String]]
  )
}
