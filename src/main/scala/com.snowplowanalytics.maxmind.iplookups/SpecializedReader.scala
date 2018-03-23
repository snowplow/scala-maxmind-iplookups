/*
 * Copyright (c) 2012-2018 Snowplow Analytics Ltd. All rights reserved.
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
import com.snowplowanalytics.maxmind.iplookups.ReaderFunctions.ReaderFunction
import scalaz._

final case class SpecializedReader(db: DatabaseReader, f: ReaderFunction) {
  def getValue(ip: InetAddress): Validation[Throwable, String] =
    Validation.fromTryCatch(f(db, ip))
}

object ReaderFunctions {
  type ReaderFunction = (DatabaseReader, InetAddress) => String

  val isp = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getIsp
  val org = (db: DatabaseReader, ip: InetAddress) => db.isp(ip).getOrganization
  val domain = (db: DatabaseReader, ip: InetAddress) => db.domain(ip).getDomain
  val connectionType = (db: DatabaseReader, ip: InetAddress) =>
    db.connectionType(ip).getConnectionType.toString
}
