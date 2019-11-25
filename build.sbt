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

lazy val root = project
  .in(file("."))
  .enablePlugins(ScalaUnidocPlugin, GhpagesPlugin)
  .settings(
    organization := "com.snowplowanalytics",
    name := "scala-maxmind-iplookups",
    version := "0.6.1",
    description := "Scala wrapper for MaxMind GeoIP2 library",
    scalaVersion := "2.12.8",
    javacOptions := BuildSettings.javaCompilerOptions,
    scalafmtOnCompile := true
  )
  .settings(BuildSettings.publishSettings)
  .settings(BuildSettings.docSettings)
  .settings(BuildSettings.coverageSettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.maxmind,
      Dependencies.catsEffect,
      Dependencies.cats,
      Dependencies.lruMap,
      Dependencies.scalaCheck,
      Dependencies.specs2
    )
  )
