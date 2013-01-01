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

// MaxMind
import com.maxmind.geoip.Location

// TODO: make an ADT of IpLocation plus UnknownLocation.

// Represents an unidentified location
val UnknownIpLocation = IpLocation(Empty, Empty, Empty, Empty, Empty, Empty, Empty, Empty, Empty, Empty)

/**
 * A stringly-typed case class wrapper around the
 * MaxMind Location class.
 * Stringly-typed because these fields will be
 * written to flatfile by Scalding.
 *
 * TODO: eurgh, what was I thinking making this stringly typed
 */
case class IpLocation(
  countryCode: String,
  countryName: String,
  region: String,
  city: String,
  latitude: Float,
  longitude: Float,
  postalCode: Option[String],
  dmaCode: Option[Int],
  areaCode: Option[Int],
  metroCode: Option[Int]
  )

/**
 * Helpers for an IpLocation
 */
object IpLocation {
  
  // Helpers to convert int or float to option field for IpLocation
  private val optionify: Int => Option[Int] = i => if (i == 0) None else Some(i)

  /**
   * Converts MaxMind Location to a stringly-typed IpLocation
   */
  implicit def location2IpLocation(loc: Location): IpLocation = IpLocation(
    countryCode = loc.countryCode,
    countryName = loc.countryName,
    region = loc.region,
    city = loc.city,
    latitude = loc.latitude,
    longitude = loc.longitude,
    postalCode = Option(loc.postalCode),
    dmaCode = optionify(loc.dma_code),
    areaCode = optionify(loc.area_code),
    metroCode = optionify(loc.metro_code)
    )
}