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

import cats.{Eval, Id}
import cats.effect.Sync
import cats.syntax.either._
import com.maxmind.geoip2.DatabaseReader

import model._

/** Data type letting you read data in maxmind's DatabaseReader. */
sealed trait SpecializedReader[F[_]] {
  def getValue[A](
    f: ReaderFunction[A],
    db: DatabaseReader,
    ip: InetAddress
  ): F[Either[Throwable, A]]
}

object SpecializedReader {
  implicit def syncSpecializedReader[F[_]: Sync]: SpecializedReader[F] = new SpecializedReader[F] {
    def getValue[A](
      f: ReaderFunction[A],
      db: DatabaseReader,
      ip: InetAddress
    ): F[Either[Throwable, A]] =
      Sync[F].delay(Either.catchNonFatal(f(db, ip)))
  }

  implicit def evalSpecializedReader: SpecializedReader[Eval] = new SpecializedReader[Eval] {
    def getValue[A](
      f: ReaderFunction[A],
      db: DatabaseReader,
      ip: InetAddress
    ): Eval[Either[Throwable, A]] =
      Eval.later(Either.catchNonFatal(f(db, ip)))
  }

  implicit def idSpecializedReader: SpecializedReader[Id] = new SpecializedReader[Id] {
    def getValue[A](
      f: ReaderFunction[A],
      db: DatabaseReader,
      ip: InetAddress
    ): Id[Either[Throwable, A]] =
      Either.catchNonFatal(f(db, ip))
  }
}

object ReaderFunctions {
  val isp     = (db: DatabaseReader, ip: InetAddress) => db.isp(ip)
  val ispName = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getIsp
  val org     = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getOrganization
  val domain  = (db: DatabaseReader, ip: InetAddress) => db.domain(ip).getDomain
  val connectionType = (db: DatabaseReader, ip: InetAddress) =>
    db.connectionType(ip).getConnectionType.toString
  val city        = (db: DatabaseReader, ip: InetAddress) => db.city(ip)
  val anonymousIp = (db: DatabaseReader, ip: InetAddress) => db.anonymousIp(ip)
}
