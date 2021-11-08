/*
 * Copyright (c) 2012-2021 Snowplow Analytics Ltd. All rights reserved.
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
import sbt._
import Keys._

// Scaladocs
import com.typesafe.sbt.sbtghpages.GhpagesPlugin.autoImport._
import com.typesafe.sbt.site.SitePlugin.autoImport.{makeSite, siteSubdirName}
import com.typesafe.sbt.SbtGit.GitKeys.{gitBranch, gitRemoteRepo}
import com.typesafe.sbt.site.SiteScaladocPlugin.autoImport._
import com.typesafe.sbt.site.preprocess.PreprocessPlugin.autoImport._

// dynver plugin
import sbtdynver.DynVerPlugin.autoImport._

// Scoverage
import scoverage.ScoverageKeys._

// Scaladocs
import sbtunidoc.ScalaUnidocPlugin.autoImport._
import com.typesafe.sbt.site.SitePlugin.autoImport._
import com.typesafe.sbt.SbtGit.GitKeys._

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
    ghpagesPushSite := (ghpagesPushSite dependsOn makeSite).value,
    ghpagesNoJekyll := false,
    gitRemoteRepo := "git@github.com:snowplow/scala-maxmind-iplookups.git",
    gitBranch := Some("gh-pages"),
    SiteScaladoc / siteSubdirName := s"${version.value}",
    Preprocess / preprocessVars := Map("VERSION" -> version.value),
    ghpagesCleanSite / excludeFilter := new FileFilter {
      def accept(f: File) = true
    }
  )

  lazy val coverageSettings = Seq(
    coverageMinimum := 90
  )
}
