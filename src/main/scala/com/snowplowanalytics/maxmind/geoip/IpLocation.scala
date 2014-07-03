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

// MaxMind
import com.maxmind.geoip.{
  LookupService,
  Location,
  regionName
}

// Concurrency
import scala.concurrent._
import scala.concurrent.duration._
import ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

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

  // Option-box a MaxMind Int, where MaxMind uses 0 to indicate None
  private val optionify: Int => Option[Int] = i => if (i == 0) None else Some(i)

  /**
   * Concurrently looks up information
   * based on an IP address from one or
   * more MaxMind LookupServices
   *
   * @param ip IP address
   * @param maxmind Location LookupService
   * @param ispService ISP LookupService
   * @param orgService Organization LookupService
   * @param domainService Domain LookupService
   * @return Tuple containing the results of the
   *         LookupServices
   */
  def multi(ip: String, maxmind: LookupService, ispService: Option[LookupService], orgService: Option[LookupService], domainService: Option[LookupService]): IpLookupResult = {

    /**
     * Creates a Future boxing the result
     * of using a lookup service on the ip
     *
     * @param service ISP, organization,
     *        or domain LookupService
     * @return the result of the lookup
     */
    def getLookupFuture(service: Option[LookupService]): Future[Option[String]] = 
      Future {
        service.map(_.getOrg(ip)).filter(_ != null)
      }

    val maxmindFuture = Future {
      Option(maxmind.getLocation(ip)).map(loc =>
        IpLocation(
          countryCode = loc.countryCode,
          countryName = loc.countryName,
          region = Option(loc.region),
          city = Option(loc.city),
          latitude = loc.latitude,
          longitude = loc.longitude,
          postalCode = Option(loc.postalCode),
          dmaCode = optionify(loc.dma_code),
          areaCode = optionify(loc.area_code),
          metroCode = optionify(loc.metro_code),
          regionName = Option(regionName.regionNameByCode(loc.countryCode, loc.region))
        )
      )
    }

    val aggregateFuture: Future[IpLookupResult] = for {
      maxmindResult <- maxmindFuture
      ispResult     <- getLookupFuture(ispService)
      orgResult     <- getLookupFuture(orgService)
      domainResult  <- getLookupFuture(domainService)
    } yield (maxmindResult, ispResult, orgResult, domainResult)

    try {
      Await.result(aggregateFuture, 4.seconds)
    } catch {
      case te: TimeoutException => (None, None, None, None)
      case e: Exception => throw e
    }
  }

}
