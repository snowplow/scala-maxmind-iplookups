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

import cats.{Eval, Id}
import cats.effect.Sync
import cats.syntax.either._
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.model.CityResponse

import model._

/** Data type letting you read data in maxmind's DatabaseReader. */
sealed trait SpecializedReader[F[_]] {
  def getValue(
    f: ReaderFunction,
    db: DatabaseReader,
    ip: InetAddress
  ): F[Either[Throwable, String]]

  def getCityValue(
    db: DatabaseReader,
    ip: InetAddress
  ): F[Either[Throwable, CityResponse]]
}

object SpecializedReader {
  implicit def synSpecializedReader[F[_]: Sync]: SpecializedReader[F] = new SpecializedReader[F] {
    def getValue(
      f: ReaderFunction,
      db: DatabaseReader,
      ip: InetAddress
    ): F[Either[Throwable, String]] =
      Sync[F].delay { Either.catchNonFatal(f(db, ip)) }

    def getCityValue(
      db: DatabaseReader,
      ip: InetAddress
    ): F[Either[Throwable, CityResponse]] =
      Sync[F].delay { Either.catchNonFatal(db.city(ip)) }
  }

  implicit def evalSpecializedReader: SpecializedReader[Eval] = new SpecializedReader[Eval] {
    def getValue(
      f: ReaderFunction,
      db: DatabaseReader,
      ip: InetAddress
    ): Eval[Either[Throwable, String]] =
      Eval.later { Either.catchNonFatal(f(db, ip)) }

    def getCityValue(
      db: DatabaseReader,
      ip: InetAddress
    ): Eval[Either[Throwable, CityResponse]] =
      Eval.later { Either.catchNonFatal(db.city(ip)) }
  }

  implicit def idSpecializedReader: SpecializedReader[Id] = new SpecializedReader[Id] {
    def getValue(
      f: ReaderFunction,
      db: DatabaseReader,
      ip: InetAddress
    ): Id[Either[Throwable, String]] =
      Either.catchNonFatal(f(db, ip))

    def getCityValue(
      db: DatabaseReader,
      ip: InetAddress
    ): Id[Either[Throwable, CityResponse]] =
      Either.catchNonFatal(db.city(ip))
  }
}

object ReaderFunctions {
  val isp    = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getIsp
  val org    = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getOrganization
  val domain = (db: DatabaseReader, ip: InetAddress) => db.domain(ip).getDomain
  val connectionType = (db: DatabaseReader, ip: InetAddress) =>
    db.connectionType(ip).getConnectionType.toString
}
