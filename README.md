# Scala MaxMind IP Lookups

[![Build Status](https://api.travis-ci.org/snowplow/scala-maxmind-iplookups.svg?branch=develop)](https://travis-ci.org/snowplow/scala-maxmind-iplookups)
[![Maven Central](https://img.shields.io/maven-central/v/com.snowplowanalytics/scala-maxmind-iplookups_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/com.snowplowanalytics/scala-maxmind-iplookups_2.12)
[![codecov](https://codecov.io/gh/snowplow/scala-maxmind-iplookups/branch/master/graph/badge.svg)](https://codecov.io/gh/snowplow/scala-maxmind-iplookups)
[![Join the chat at https://gitter.im/snowplow/scala-maxmind-iplookups](https://badges.gitter.im/snowplow/scala-maxmind-iplookups.svg)](https://gitter.im/snowplow/scala-maxmind-iplookups?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## Introduction

This is a Scala wrapper for the MaxMind [Java Geo-IP2][java-lib] library. The main benefits of using
this wrapper over directly calling the Java library from Scala are:

1. **Provides a common interface to four MaxMind databases** - it works with MaxMind's databases for
looking up geographic location, ISP, domain, and connection type from an IP address
2. **Better type safety** - the MaxMind Java library is somewhat null-happy. This wrapper uses
Option-boxing wherever possible
3. **Better performance** - as well as or instead of using MaxMind's own caching (`CHMCache`), you
can also configure an LRU (Least Recently Used) cache of variable size

## Installation

The latest version of scala-maxmind-iplookups is **0.7.1** and is compatible with Scala 2.13.

Add this to your SBT config:

```scala
val maxmindIpLookups = "com.snowplowanalytics" %% "scala-maxmind-iplookups" % "0.7.1"
```

Retrieve the `GeoLite2-City.mmdb` file from the [MaxMind downloads page][maxmind-downloads]
([direct link][geolitecity-dat]).

MaxMind also has databases for looking up [ISPs][maxmind-isp], [domain names][maxmind-domain], and
[connection types][maxmind-connection-type] from IP addresses. Scala MaxMind IP Lookups supports all
of these.

## Usage

Here is a simple usage example, performing just a geographic lookup and not the ISP, domain, or
connection type lookups:

```scala
import cats.effect.IO
import com.snowplowanalytics.maxmind.iplookups.IpLookups

val result = (for {
  ipLookups <- CreateIpLookups[IO].createFromFilenames(
    geoFile = Some("/opt/maxmind/GeoLite2-City.mmdb")
    ispFile = None,
    domainFile = None,
    connectionTypeFile = None,
    memCache = false,
    lruCacheSize = 20000
  )
  lookup <- ipLookups.performLookups("175.16.199.0")
} yield lookup).unsafeRunSync()

result.ipLocation match {
  case Some(Right(loc)) =>
    println(loc.countryCode)   // => "CN"
    println(loc.countryName)   // => "China"
  case _ =>
    println("Lookup failed")
}
```

`cats.Id` is also supported:

```scala
import cats.Id

val idResult: IpLookupResult = {
  val ipLookups = CreateIpLookups[Id].createFromFilenames(
    geoFile = Some("/opt/maxmind/GeoLite2-City.mmdb")
    ispFile = None,
    domainFile = None,
    connectionTypeFile = None,
    memCache = false,
    lruCacheSize = 20000
  )
  ipLookups.performLookups("175.16.199.0")
}
```

Note that `GeoLite2-City.mmdb` is updated by MaxMind each month.

For further usage examples for Scala MaxMind IP Lookups, please see the tests in
[`IpLookupsTest.scala`][iplookupstest-scala]. The test suite uses test databases provided by
MaxMind.

## Implementation details

### IpLookups constructor

The signature is as follows:

```scala
final case class IpLookups(
  geoFile: Option[File],
  ispFile: Option[File],
  domainFile: Option[File],
  connectionTypeFile: Option[File],
  memCache: Boolean = true,
  lruCache: Int = 10000
)
```

`CreateIpLookups` proposes an alternative constructor which takes `Option[String]`
as file paths to the databases instead:

```scala
def createFromFilenames(
  geoFile: Option[String],
  ispFile: Option[String],
  domainFile: Option[String],
  connectionTypeFile: Option[String],
  memCache: Boolean = true,
  lruCache: Int = 10000
)
```

The first four arguments are the MaxMind databases from which the lookup should be performed.
`geoFile`, `ispFile`, `domainFile`, and `connectionTypeFile` refer respectively to MaxMind's
databases for looking up location, ISP, domain, and connection type based on an IP address. They are
all wrapped in `Option`, so if you don't have access to all of them, just pass in `None` as in the
example above.

In both signatures, the `memCache` flag is set to `true` by default. This flag enables MaxMind's own
caching (`CHMCache`).

The `lruCache` value defaults to `10000` - meaning Scala MaxMind IP Lookups will maintain an LRU
cache of 10,000 values, which it will check prior to making a MaxMind lookup. To disable the LRU
cache, set its size to zero, i.e. `lruCache = 0`.

### Returned value

The `performLookups(ip)` method returns a:

```scala
final case class IpLookupResult(
  ipLocation: Option[Either[Throwable, IpLocation]],
  isp: Option[Either[Throwable, String]],
  organization: Option[Either[Throwable, String]],
  domain: Option[Either[Throwable, String]],
  connectionType: Option[Either[Throwable, String]]
)
```

The first element is the result of the geographic location lookup. It is either `None` (if no
geographic lookup database was provided) or `Some(ipLocation)`, where `ipLocation` is an instance of
the `IpLocation` case class described below. The other three elements in the tuple are `Option`s
wrapping the results of the other four possible lookups: ISP, organization, domain, and connection
type.

Note that enabling providing an ISP database will return an `organization` in addition to an `isp`.

### IpLocation case class

The geographic lookup returns an `IpLocation` case class instance with the following structure:

```scala
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
```

### An example using multiple databases

This example shows how to do a lookup using all four databases.

```scala
import com.snowplowanalytics.maxmind.iplookups.IpLookups

val lookupResult = (for {
  ipLookups <- CreateIpLookups[IO].createFromFilenames(
    geoFile = Some("/opt/maxmind/GeoLite2-City.mmdb"),
    ispFile = Some("/opt/maxmind/GeoIP2-ISP.mmdb"),
    domainFile = Some("/opt/maxmind/GeoIP2-Domain.mmdb"),
    connectionType = Some("/opt/maxmind/GeoIP2-Connection-Type.mmdb"),
    memCache = false,
    lruCache = 10000
  )
  lookupResult <- ipLookups.performLookups("70.46.123.145")
} yield lookupResult).unsafeRunSync()

// Geographic lookup
println(lookupResult.ipLocation).map(_.countryName) // => Some(Right("United States"))
println(lookupResult.ipLocation).map(_.regionName)  // => Some(Right("Florida"))

// ISP lookup
println(lookupResult.isp) // => Some(Right("FDN Communications"))

// Organization lookup
println(lookupResult.organization) // => Some(Right("DSLAM WAN Allocation"))

// Domain lookup
println(lookupResult.domain) // => Some(Right("nuvox.net"))

// Connection type lookup
println(lookupResult.connectionType) // => Some(Right("Dialup"))
```

### LRU cache

We recommend trying different LRU cache sizes to see what works best for you.

## Building etc

Assuming you already have SBT installed:

    $ git clone git://github.com/snowplow/scala-maxmind-iplookups.git
    $ cd scala-maxmind-iplookups
    $ sbt test
    <snip>
    [info] Passed: : Total 276, Failed 0, Errors 0, Passed 276, Skipped 0

## GeoLite Legacy discontinuation

Maxmind are [discontinuing updates](https://dev.maxmind.com/geoip/geoip2/geolite2/) for the GeoLite
Legacy databases in April 2018.

> We will be discontinuing updates to the GeoLite Legacy databases as of April 1, 2018. You will still be able to download the April 2018 release until January 2, 2019. GeoLite Legacy users will need to update their integrations in order to switch to the free GeoLite2 or commercial GeoIP databases by April 2018.

> For more information, please visit our Support Center.

> In addition, in 2019, latitude and longitude coordinates in the GeoLite2 databases will be removed.* Latitude and longitude coordinates will continue to be provided in GeoIP2 databases. Please check back for updates.

As such we recommend upgrading to version 0.4.0 as soon as possible

## Copyright and license

Copyright 2012-2020 Snowplow Analytics Ltd.

Licensed under the [Apache License, Version 2.0][license] (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[java-lib]: https://github.com/maxmind/GeoIP2-java

[iplookupstest-scala]: https://github.com/snowplow/scala-maxmind-iplookups/blob/master/src/test/scala/com.snowplowanalytics.maxmind.iplookups/IpLookupsTest.scala

[maxmind-downloads]: https://dev.maxmind.com/geoip/geoip2/downloadable/#MaxMind_APIs
[maxmind-isp]: https://www.maxmind.com/en/geoip2-isp-database
[maxmind-domain]: https://www.maxmind.com/en/geoip2-domain-name-database
[maxmind-connection-type]: https://www.maxmind.com/en/geoip2-connection-type-database
[geolitecity-dat]: http://geolite.maxmind.com/download/geoip/database/GeoLite2-City.tar.gz

[license]: http://www.apache.org/licenses/LICENSE-2.0
