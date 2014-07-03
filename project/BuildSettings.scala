/*
 * Copyright (c) 2012-2013 Snowplow Analytics Ltd. All rights reserved.
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

// SBT
import sbt._
import Keys._

object BuildSettings {

  // Basic settings for our app
  lazy val basicSettings = Seq[Setting[_]](
    organization  := "com.snowplowanalytics",
    version       := "0.1.0",
    description   := "Scala wrapper for MaxMind GeoIP library",
    scalaVersion  := "2.9.3",
    crossScalaVersions := Seq("2.9.3", "2.10.0", "2.10.4"),
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    resolvers     ++= Dependencies.resolutionRepos
  )

  // For MaxMind support
  import Dependencies._
  lazy val maxmindSettings = Seq(

    // Download the GeoLite City for our test suite
    resourceGenerators in Test <+= (resourceManaged in Test) map { out =>
      val gzRemote = new URL(Urls.maxmindData)
      val datLocal = out / "maxmind" / "GeoLiteCity.dat"
      
      // Only fetch if we don't already have it (because MaxMind 403s if you download GeoIP.dat.gz too frequently)
      if (!datLocal.exists()) {
        // TODO: replace this with simply IO.gunzipURL(gzRemote, out / "maxmind") when https://github.com/harrah/xsbt/issues/529 implemented
        val gzLocal = out / "GeoLiteCity.dat.gz"        
        IO.download(gzRemote, gzLocal)
        IO.createDirectory(out / "maxmind")
        IO.gunzip(gzLocal, datLocal)
        IO.delete(gzLocal)
        // gunzipURL(gzRemote, out / "maxmind")
      }
      datLocal.get
    }
  )

  // Publish settings
  // TODO: update with ivy credentials etc when we start using Nexus
  lazy val publishSettings = Seq[Setting[_]](
   
    publishTo <<= version { version =>
      val keyFile = (Path.userHome / ".ssh" / "admin_keplar.osk")
      val basePath = "/var/www/maven.snplow.com/prod/public/%s".format {
        if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases/"
      }
      Some(Resolver.sftp("SnowPlow Analytics Maven repository", "prodbox", 8686, basePath) as ("admin", keyFile))
    }
  )

  lazy val buildSettings = basicSettings ++ maxmindSettings ++ publishSettings
}
