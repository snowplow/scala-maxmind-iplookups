/* Copyright (c) 2012-2017 Snowplow Analytics Ltd. and Micronautics Research Corporation. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under. */

package com.snowplowanalytics.maxmind

/** @param maybeIpLocation result of the geographic location lookup.
	*                        It is either `None` (if no geographic lookup database was provided or if the lookup returned `null`)
	*                        or `Some(ipLocation)`, where `ipLocation` is an instance of the `IpLocation` case class.
	* @param maybeIsp result of the ISP lookup
	* @param maybeOrg result of the organization lookup
	* @param maybeDomain result of the domain lookup
	* @param maybeNetSpeed result of net speed lookup */
case class IpLookupResult(
	maybeIpLocation: Option[IpLocation] = None,
	maybeIsp: Option[String] = None,
	maybeOrg: Option[String] = None,
	maybeDomain: Option[String] = None,
	maybeNetSpeed: Option[String] = None
)
