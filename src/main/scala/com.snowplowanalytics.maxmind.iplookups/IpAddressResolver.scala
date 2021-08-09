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

/** Data type letting you resolve IP address */
sealed trait IpAddressResolver[F[_]] {
  def resolve(ip: String): F[Either[Throwable, InetAddress]]

  protected def getIpAddress(ip: String): Either[Throwable, InetAddress] =
    Either.catchNonFatal(InetAddress.getByName(ip))
}

object IpAddressResolver {
  implicit def syncIpAddressResolver[F[_]: Sync]: IpAddressResolver[F] = new IpAddressResolver[F] {
    def resolve(ip: String): F[Either[Throwable, InetAddress]] =
      Sync[F].delay(getIpAddress(ip))
  }

  implicit def evalIpAddressResolver: IpAddressResolver[Eval] = new IpAddressResolver[Eval] {
    def resolve(ip: String): Eval[Either[Throwable, InetAddress]] =
      Eval.later(getIpAddress(ip))
  }

  implicit def idIpAddressResolver: IpAddressResolver[Id] = new IpAddressResolver[Id] {
    def resolve(ip: String): Id[Either[Throwable, InetAddress]] =
      getIpAddress(ip)
  }
}
