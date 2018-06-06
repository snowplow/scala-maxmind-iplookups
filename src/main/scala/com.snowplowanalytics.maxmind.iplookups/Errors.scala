/*
 * Copyright (c) 2015-2017 Snowplow Analytics Ltd. All rights reserved.
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

object Errors {

  sealed trait IpLookupError {
    val message: String
    override def toString = s"IpLookup ${this.getClass.getSimpleName} " + message
  }
  case class IOException(message: String) extends java.io.IOException(message) with IpLookupError

  case class GeoIp2Exception(message: String)
      extends com.maxmind.geoip2.exception.GeoIp2Exception(message: String)
      with IpLookupError

  case class UnknownHostException(message: String)
      extends java.net.UnknownHostException(message: String)
      with IpLookupError

  //com.maxmind.geoip2.exception.AddressNotFoundException is a final class
  case class AddressNotFoundException(ex: com.maxmind.geoip2.exception.AddressNotFoundException)
      extends IpLookupError {
    val message: String   = ex.getMessage
    override def toString = s"IpLookup ${ex.getClass.getSimpleName} " + message
  }

}
