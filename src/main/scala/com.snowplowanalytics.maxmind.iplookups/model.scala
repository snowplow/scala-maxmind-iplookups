/*
 * Copyright (c) 2012-2022 Snowplow Analytics Ltd. All rights reserved.
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

import cats.data.ValidatedNel
import cats.syntax.traverse._
import cats.syntax.either._
import cats.syntax.apply._
import cats.instances.option._
import cats.instances.either._

import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.{AnonymousIpResponse, AsnResponse, CityResponse, IspResponse}

object model {
  type ReaderFunction[A] = (DatabaseReader, InetAddress) => A

  type Error[A] = Either[Throwable, A]

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

  /** A case class wrapper around the MaxMind AnonymousIp class. */
  final case class AnonymousIp(
    ipAddress: String,
    isAnonymous: Boolean,
    isAnonymousVpn: Boolean,
    isHostingProvider: Boolean,
    isPublicProxy: Boolean,
    isTorExitNode: Boolean
  )

  /** A case class wrapper around the MaxMind AsnResponse class. */
  final case class Asn(
    autonomousSystemNumber: Option[Long],
    autonomousSystemOrganization: Option[String]
  )

  /** A case class wrapper around the MaxMind IspResponse class. */
  final case class Isp(
    name: String,
    organization: String,
    asn: Asn
  )

  /** Companion class contains a constructor which takes a MaxMind CityResponse. */
  object IpLocation {

    /**
     * Constructs an IpLocation instance from a MaxMind CityResponse instance.
     * @param cityResponse MaxMind CityResponse object
     * @return IpLocation
     */
    def apply(cityResponse: CityResponse): IpLocation = {
      // Try to bypass bincompat problem with Spark Enrich,
      // Delete once Spark Enrich is deprecated
      val isInEuropeanUnion =
        try cityResponse.getCountry.isInEuropeanUnion
        catch {
          case _: NoSuchMethodError => false
        }
      IpLocation(
        countryCode = cityResponse.getCountry.getIsoCode,
        countryName = cityResponse.getCountry.getName,
        region = Option(cityResponse.getMostSpecificSubdivision.getIsoCode),
        city = Option(cityResponse.getCity.getName),
        latitude = Option(cityResponse.getLocation.getLatitude).map(_.toFloat).getOrElse(0f),
        longitude = Option(cityResponse.getLocation.getLongitude).map(_.toFloat).getOrElse(0f),
        timezone = Option(cityResponse.getLocation.getTimeZone),
        postalCode = Option(cityResponse.getPostal.getCode),
        metroCode = Option(cityResponse.getLocation.getMetroCode).map(_.toInt),
        regionName = Option(cityResponse.getMostSpecificSubdivision.getName),
        isInEuropeanUnion = isInEuropeanUnion,
        continent = cityResponse.getContinent.getName,
        accuracyRadius = cityResponse.getLocation.getAccuracyRadius
      )
    }
  }

  /** Companion class contains a constructor which takes a MaxMind AnonymousIp. */
  object AnonymousIp {

    /**
     * Constructs an AnonymousIp instance from a MaxMind AnonymousIp instance.
     * @param anonymousIP MaxMind AnonymousIp object
     * @return AnonymousIp
     */
    def apply(anonymousIpResponse: AnonymousIpResponse): AnonymousIp = {

      AnonymousIp(
        ipAddress = anonymousIpResponse.getIpAddress,
        isAnonymous = anonymousIpResponse.isAnonymous,
        isAnonymousVpn = anonymousIpResponse.isAnonymousVpn,
        isHostingProvider = anonymousIpResponse.isHostingProvider,
        isPublicProxy = anonymousIpResponse.isPublicProxy,
        isTorExitNode = anonymousIpResponse.isTorExitNode
      )
    }

  }

  /** Companion class contains a constructor which takes a MaxMind AsnResponse. */
  object Asn {

    /**
     * Constructs an Asn instance from a MaxMind AsnResponse instance.
     * @param asnResponse MaxMind AsnResponse object
     * @return Asn
     */
    def apply(asnResponse: AsnResponse): Asn =
      Asn(
        autonomousSystemNumber = Option(asnResponse.getAutonomousSystemNumber).map(_.toLong),
        autonomousSystemOrganization = Option(asnResponse.getAutonomousSystemOrganization)
      )
  }

  /** Companion class contains a constructor which takes a MaxMind IspResponse. */
  object Isp {

    /**
     * Constructs an Isp instance from a MaxMind IspResponse instance.
     * @param ispResponse MaxMind IspResponse object
     * @return Isp
     */
    def apply(ispResponse: IspResponse): Isp =
      Isp(
        name = ispResponse.getIsp,
        organization = ispResponse.getOrganization,
        asn = Asn(ispResponse)
      )
  }

  /** Result of MaxMind lookups */
  final case class IpLookupResult(
    ipLocation: Option[Either[Throwable, IpLocation]],
    isp: Option[Either[Throwable, String]],
    organization: Option[Either[Throwable, String]],
    asn: Option[Either[Throwable, Asn]],
    domain: Option[Either[Throwable, String]],
    connectionType: Option[Either[Throwable, String]],
    anonymousIp: Option[Either[Throwable, AnonymousIp]]
  ) {
    // Combine all errors if any
    def results: ValidatedNel[
      Throwable,
      (
        Option[IpLocation],
        Option[String],
        Option[String],
        Option[Asn],
        Option[String],
        Option[String],
        Option[AnonymousIp]
      )
    ] = {
      val location   = ipLocation.sequence[Error, IpLocation].toValidatedNel
      val provider   = isp.sequence[Error, String].toValidatedNel
      val org        = organization.sequence[Error, String].toValidatedNel
      val asnRes     = asn.sequence[Error, Asn].toValidatedNel
      val dom        = domain.sequence[Error, String].toValidatedNel
      val connection = connectionType.sequence[Error, String].toValidatedNel
      val anonymous  = anonymousIp.sequence[Error, AnonymousIp].toValidatedNel

      (location, provider, org, asnRes, dom, connection, anonymous).tupled
    }
  }
}
