# Scala MaxMind IP Lookups [![Build Status](https://travis-ci.org/snowplow/scala-maxmind-iplookups.png)](https://travis-ci.org/snowplow/scala-maxmind-iplookups)

## Introduction

This is a Scala wrapper for the MaxMind [Java Geo-IP] [java-lib] library. The main benefits of using this wrapper over directly calling the Java library from Scala are:

1. **Provides a common interface to four MaxMind databases** - it works with MaxMind's databases for looking up geographic location, ISP, organization, and domain from an IP address
2. **Easier to setup/test** - the SBT project definition makes it easy to download and test
3. **Better type safety** - the MaxMind Java library is somewhat null-happy. This wrapper uses Option boxing wherever possible
4. **Better performance** - as well as or instead of using MaxMind's own caching (`GEOIP_MEMORY_CACHE`), you can also configure an LRU (Least Recently Used) cache of variable size

## Installation

The latest version of scala-maxmind-iplookups is **0.2.0** and is compatible with Scala version 2.9.x, where x is at least 3, and Scala version 2.10.x.

Add this to your SBT config:

```scala
// Resolvers
val snowplowRepo = "SnowPlow Repo" at "http://maven.snplow.com/releases/"
val twitterRepo  = "Twitter Maven Repo" at "http://maven.twttr.com/"

// Dependency
val maxmindIpLookups = "com.snowplowanalytics"  %% "scala-maxmind-iplookups"  % "0.2.0"
```

Note the double percent (`%%`) between the group and artifactId. That'll ensure you get the right package for your Scala version.

Retrieve the `GeoLiteCity.dat` file from the [MaxMind downloads page] [maxmind-downloads] ([direct link] [geolitecity-dat]).

MaxMind also has databases for looking up [ISPs][maxmind-isp], [organizations][maxmind-org], and [domain names][maxmind-domain] from IP addresses. Scala MaxMind IP Lookups supports all of these.

## Usage

Here is a simple usage example, performing just a geographic lookup and not the ISP, organization, or domain lookups:

```scala
import com.snowplowanalytics.maxmind.iplookups.IpLookups

val ipLookups = IpLookups(geoFile = Some("/opt/maxmind/GeoLiteCity.dat"), ispFile = None,
                  orgFile = None, domainFile = None, memCache = false, lruCache = 20000)

for (loc <- ipLookups.performLookups("213.52.50.8")._1) {
  println(loc.countryCode)   // => "NO"
  println(loc.countryName)   // => "Norway" 
}
```

Note that `GeoLiteCity.dat` is updated by MaxMind each month - see [maxmind-geolite-update] [maxmind-geolite-update] for a Python script that pings MaxMind regularly to keep your local copy up-to-date.

For further usage examples for Scala MaxMind IP Lookups, please see the tests in [`IpLookupsTest.scala`] [iplookupstest-scala]. The test suite uses test databases provided by MaxMind.

## Implementation details

### IpLookups constructor

The signature is as follows:

```scala
class IpLookups(geoFile: Option(File), ispFile: Option(File), 
                orgFile: Option(File), domainFile: Option(File), 
                memCache: Boolean = true, lruCache: Int = 10000)
```

In the `IpLookups` companion object there is an alternative constructor which takes Option[String] as the time of the file arguments instead:

```scala
def apply(geoFile: Option[String], ispFile: Option[String], 
          orgFile: Option[String], domainFile: Option[String], 
          memCache: Boolean = true, lruCache: Int = 10000)
```

The first four arguments are the MaxMind databases from which the lookup should be performed. `geoFile`, `ispFile`, `orgFile`, and `domainFile` refer respectively to MaxMind's databases for looking up location, ISP, organization, and domain based on an IP address. They are all wrapped in `Option`, so if you don't have access to all of them, just pass in `None` as in the example above. The ones you do pass in must be wrapped in `Some`.

In both signatures, the `memCache` flag is set to `true` by default. This flag enables MaxMind's own caching (`GEOIP_MEMORY_CACHE`).

The `lruCache` value defaults to `10000` - meaning Scala MaxMind IP Lookups will maintain an LRU cache of 10,000 values, which it will check prior to making a MaxMind lookup. To disable the LRU cache, set its size to zero, i.e. `lruCache = 0`.

### Returned value

The `performLookups(ip)` method returns a `Tuple4[Option[IpLocation], Option[String], Option[String], Option[String]]`. The first element is the result of the geographic location lookup. It is either `None` (if no geographic lookup database was provided or if the lookup returned `null`) or `Some(ipLocation)`, where `ipLocation` is an instance of the `IpLocation` case class described below. The other three elements in the tuple are `Option`s wrapping the results of the other three possible lookups: ISP, organization, and domain.

### IpLocation case class

The geographic lookup returns an `IpLocation` case class instance with the following structure:

```scala
case class IpLocation(
  countryCode: String,
  countryName: String,
  region: Option[String],
  city: Option[String],
  latitude: Float,
  longitude: Float,
  timezone: Option[String],
  postalCode: Option[String],
  dmaCode: Option[Int],
  areaCode: Option[Int],
  metroCode: Option[Int],
  regionName: Option[String]  
  )
```

### An example using multiple databases

This example shows how to do a lookup using all four databases.

```scala
import com.snowplowanalytics.maxmind.iplookups.IpLookups

val ipLookups = IpLookups(geoFile = Some("/opt/maxmind/GeoLiteCity.dat"),
                  ispFile = Some("/opt/maxmind/GeoIPISP.dat"),
                  orgFile = Some("/opt/maxmind/GeoIPOrg.dat"),
                  domainFile = Some("/opt/maxmind/GeoIPDomain.dat"),
                  memCache = false, lruCache = 20000)

val lookupResult = ipLookups.performLookups("70.46.123.145")

// Geographic lookup
println(lookupResult._1).map(_.countryName) //=> Some("United States")
println(lookupResult._1).map(_.regionName)  // => Some("Florida")

// ISP lookup
println(lookupResult._2) // => Some("FDN Communications")

// Organization lookup
println(lookupResult._3) // => Some("DSLAM WAN Allocation")

// Domain lookup
println(lookupResult._4) // => Some("nuvox.net")
```

### LRU cache

We recommend trying different LRU cache sizes to see what works best for you.

Please note that the LRU cache is **not** thread-safe ([see this note] [twitter-lru-cache]). Switch it off if you are working with threads.

## Building etc

Assuming you already have SBT installed:

    $ git clone git://github.com/snowplow/scala-maxmind-iplookups.git
    $ cd scala-maxmind-iplookups
    $ sbt test
    <snip>
    [info] Passed: : Total 276, Failed 0, Errors 0, Passed 276, Skipped 0

## Roadmap

Nothing planned currently, although we want to look into Specs2's data tables and see if they would be a better fit for the unit tests.

## Copyright and license

Copyright 2012-2013 Snowplow Analytics Ltd.

Licensed under the [Apache License, Version 2.0] [license] (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[java-lib]: http://www.maxmind.com/download/geoip/api/java/

[iplookupstest-scala]: https://github.com/snowplow/scala-maxmind-iplookups/blob/master/src/test/scala/com.snowplowanalytics.maxmind.iplookups/IpLookupsTest.scala

[twitter-lru-cache]: http://twitter.github.com/commons/apidocs/com/twitter/common/util/caching/LRUCache.html

[maxmind-downloads]: http://dev.maxmind.com/geoip/legacy/geolite
[maxmind-isp]: https://www.maxmind.com/en/isp
[maxmind-org]: https://www.maxmind.com/en/organization
[maxmind-domain]: https://www.maxmind.com/en/domain
[geolitecity-dat]: http://geolite.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz
[maxmind-geolite-update]: https://github.com/psychicbazaar/maxmind-geolite-update

[license]: http://www.apache.org/licenses/LICENSE-2.0
