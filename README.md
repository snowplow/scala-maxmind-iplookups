# Scala MaxMind Geo-IP

## Introduction

This is a Scala wrapper for the MaxMind [Java Geo-IP] [java-lib] library. The main benefits of using this wrapper over directly calling the Java library from Scala are:

1. **Easier to setup/test** - the SBT project definition automatically pulls down the latest MaxMind Java code and `GeoLiteCity.dat`
2. **Better type safety** - the MaxMind Java library is somewhat null-happy. This wrapper uses Option boxing wherever possible
3. **Better performance** - as well as or instead of using MaxMind's own caching (`GEOIP_MEMORY_CACHE`), you can also configure an LRU (Least Recently Used) cache of variable size

## Installation

Add this to your SBT config:

```scala
// Resolver
val snowplowRepo = "SnowPlow Repo" at "http://maven.snplow.com/releases/"

// Dependency
val maxmindGeoip = "com.snowplowanalytics"   % "scala-maxmind-geoip"   % "0.0.1"
```

## Usage

Here is a simple usage example, using the bundled `GeoLiteCity.dat` file:

```scala
import com.snowplowanalytics.maxmind.geoip.IpGeo

val dbFilepath = getClass.getResource("/maxmind/GeoLiteCity.dat").toURI()
val ipGeo = new IpGeo(dbFile = new java.io.File(dbFilepath), memCache = false, lruCache = 20000)

for (loc <- ipGeo.getLocation("213.52.50.8")) {
  println(loc.countryCode)   // => "NO"
  println(loc.countryName)   // => "Norway" 
}
```

Given that `GeoLiteCity.dat` is updated by MaxMind each month, we **strongly recommend** maintaining an up-to-date `GeoLiteCity.dat` file outside of the jar and using that for your geo-IP lookups, and only using the bundled `GeoLiteCity.dat` file for testing purposes. See [maxmind-geolite-update] [maxmind-geolite-update] for a Python script that does this.

For further usage examples for Scala MaxMind Geo-IP, please see the tests in [`IpGeoTest.scala`] [ipgeotest-scala].

## Technical details

### IpGeo constructor

The signature is as follows:

```scala
class IpGeo(dbFile: File, memCache: Boolean = true, lruCache: Int = 10000)
```

The `memCache` flag is set to true by default. This flag enables MaxMind's own caching (`GEOIP_MEMORY_CACHE`).

The `lruCache` value defaults to 10,000 - meaning Scala MaxMind Geo-IP will maintain an LRU cache of 10,000 values, which it will check prior to making a MaxMind lookup. To disable the LRU cache, set its size to zero, i.e. `lruCache = 0`.

### IpLocation case class

The `getLocation(ip)` method returns an `IpLocation` case class with the following structure:

```scala
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
  metroCode: Option[Int]
  )
```

### LRU cache

We recommend trying different LRU cache sizes to see what works best for you.

Please note that the LRU cache is **not** thread-safe ([see this note] [twitter-lru-cache]). Switch it off if you are working with threads.

## Building etc

Assuming you already have SBT installed:

    $ git clone git://github.com/snowplow/scala-maxmind-geoip.git
    $ cd scala-maxmind-geoip
    $ sbt test
    <snip>
    [info] Passed: : Total 186, Failed 0, Errors 0, Passed 186, Skipped 0

If you want to build a 'fat jar':

    $ sbt assembly 

The 'fat jar' is now available as:

    target/scala-maxmind-geoip-0.0.1-fat.jar

## Roadmap

Nothing planned currently, although I want to look into Specs2's data tables and see if they would be a better fit for the unit tests.

## Copyright and license

Copyright 2012 SnowPlow Analytics Ltd.

Licensed under the [Apache License, Version 2.0] [license] (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[java-lib]: http://www.maxmind.com/download/geoip/api/java/

[ipgeotest-scala]: https://github.com/snowplow/scala-maxmind-geoip/blob/master/src/test/scala/com/snowplowanalytics/maxmind/geoip/IpGeoTest.scala

[twitter-lru-cache]: http://twitter.github.com/commons/apidocs/com/twitter/common/util/caching/LRUCache.html

[maxmind-geolite-update]: https://github.com/psychicbazaar/maxmind-geolite-update

[license]: http://www.apache.org/licenses/LICENSE-2.0