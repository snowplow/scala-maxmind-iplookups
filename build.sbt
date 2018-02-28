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

import Dependencies._

lazy val project = Project("scala-maxmind-iplookups", file("."))
  .settings(
    organization  := "com.snowplowanalytics",
    version       := "0.4.0",
    description   := "Scala wrapper for MaxMind GeoIP library",
    scalaVersion  := "2.11.12",
    crossScalaVersions := Seq("2.11.12", "2.12.4"),
    scalacOptions := Seq("-deprecation", "-encoding", "utf8"),
    resolvers     ++= resolutionRepos,
    libraryDependencies ++= Seq(maxmind, collUtils, specs2)
  )
