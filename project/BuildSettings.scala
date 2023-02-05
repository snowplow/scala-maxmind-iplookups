/*
 * Copyright (c) 2012-2022 Snowplow Analytics Ltd. All rights reserved.
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
import sbt.Keys._
import sbt._

// Scaladocs
import com.typesafe.sbt.site.SitePlugin.autoImport.siteSubdirName
import com.typesafe.sbt.site.SiteScaladocPlugin.autoImport._

// dynver plugin
import sbtdynver.DynVerPlugin.autoImport._

// Scoverage
import scoverage.ScoverageKeys._

object BuildSettings {

  lazy val javaCompilerOptions = Seq(
    "-source", "11",
    "-target", "11"
  )

//  lazy val publishSettings = Seq(
//    publishArtifact := true,
//    Test / publishArtifact := false,
//    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
//    pomIncludeRepository := { _ => false },
//    homepage := Some(url("http://snowplowanalytics.com")),
//    ThisBuild / dynverVTagPrefix := false, // Otherwise git tags required to have v-prefix
//    developers := List(
//      Developer(
//        "Snowplow Analytics Ltd",
//        "Snowplow Analytics Ltd",
//        "support@snowplowanalytics.com",
//        url("https://snowplowanalytics.com")
//      )
//    ),
//  )

  lazy val docSettings = Seq(
    SiteScaladoc / siteSubdirName := s"${version.value}",
  )

  lazy val coverageSettings = Seq(
    coverageMinimumStmtTotal := 90
  )
}
