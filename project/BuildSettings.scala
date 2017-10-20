/*
 * Copyright (c) 2012-2013 Snowplow Analytics Ltd and Micronautics Research Corporation. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under. */

import sbt._
import Keys._

object BuildSettings {
  lazy val basicSettings: Seq[Setting[_]] = Seq[Setting[_]](
    organization  := "com.snowplowanalytics",
    version       := "0.4.0",
    description   := "Scala wrapper for MaxMind GeoIP library",
    scalaVersion  := "2.12.4",
    crossScalaVersions := Seq("2.11.11", "2.12.4"),
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    resolvers     ++= Dependencies.resolutionRepos
  )

  // TODO: update with ivy credentials etc if we start using Nexus
  lazy val publishSettings: Seq[Setting[_]] = Seq[Setting[_]](
    publishTo <<= version { version =>
      val basePath = "target/repo/%s".format {
        if (version.trim.endsWith("SNAPSHOT")) "snapshots/" else "releases/"
      }
      Some(Resolver.file("Local Maven repository", file(basePath)) transactional())
    }
  )

  lazy val buildSettings: Seq[Setting[_]] = basicSettings ++ publishSettings
}
