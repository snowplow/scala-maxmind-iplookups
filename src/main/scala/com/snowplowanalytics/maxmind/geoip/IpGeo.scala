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

// LRU
import com.twitter.util.LruMap

// MaxMind
import com.maxmind.geoip.{Location, LookupService}

// This library
import IpLocation._

/**
 * IpGeo is a wrapper around MaxMind's own LookupService.
 *
 * Two main differences:
 *
 * 1. getLocation(ip: String) now returns an IpLocation
 *    case class, not a raw MaxMind Location
 * 2. IpGeo introduces an LRU cache to improve
 *    lookup performance
 *
 * Inspired by:
 * https://github.com/jt6211/hadoop-dns-mining/blob/master/src/main/java/io/covert/dns/geo/IpGeo.java
 */
class IpGeo(dbFile: File, fromDisk: Boolean = false, cacheSize: Int = 10000) {

  // Initialise the cache
  private val lru = new LruMap[String, Option[IpLocation]](cacheSize) // Of type mutable.Map[String, Option[IpLocation]]

  // Configure the lookup service
  private val options = if (fromDisk) LookupService.GEOIP_STANDARD else LookupService.GEOIP_MEMORY_CACHE
  private val maxmind = new LookupService(dbFile, options)

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   *
   * Don't confuse the LRU returning None (meaning that no
   * cache entry could be found), versus an extant cache entry
   * containing None (meaning that the IP address is unknown).
   */
  def getLocation(ip: String): Option[IpLocation] = lru.get(ip) match {
    case Some(loc) => loc // In the LRU cache
    case None =>
      val loc = Option(maxmind.getLocation(ip)) map IpLocation.apply
      lru.put(ip, loc)
      loc
  }
}
