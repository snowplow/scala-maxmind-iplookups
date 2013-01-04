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
 * IpGeo is a Scala wrapper around MaxMind's own LookupService Java class.
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
  private val lru = if (cacheSize > 0) new LruMap[String, Option[IpLocation]](cacheSize) else null // Of type mutable.Map[String, Option[IpLocation]]

  // Configure the lookup service
  private val options = if (fromDisk) LookupService.GEOIP_STANDARD else LookupService.GEOIP_MEMORY_CACHE
  private val maxmind = new LookupService(dbFile, options)

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   */
  def getLocation = if (cacheSize <= 0) getLocationWithoutCache _ else getLocationWithCache _

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   *
   * This version does not use the LRU cache.
   */
  private def getLocationWithoutCache(ip: String): Option[IpLocation] =
    Option(maxmind.getLocation(ip)) map IpLocation.apply

  /**
   * Returns the MaxMind location for this IP address
   * as an IpLocation, or None if MaxMind cannot find
   * the location.
   *
   * This version uses and maintains the LRU cache.
   *
   * Don't confuse the LRU returning None (meaning that no
   * cache entry could be found), versus an extant cache entry
   * containing None (meaning that the IP address is unknown).
   */
  private def getLocationWithCache(ip: String): Option[IpLocation] = lru.get(ip) match {
    case Some(loc) => loc // In the LRU cache
    case None => // Not in the LRU cache
      val loc = Option(maxmind.getLocation(ip)) map IpLocation.apply
      lru.put(ip, loc)
      loc
  }
}