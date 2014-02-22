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
import sbt._
import Keys._

object Dependencies {
  val resolutionRepos = Seq(
    ScalaToolsSnapshots,
    "Twitter Maven Repo" at "http://maven.twttr.com/" // For Twitter's util functions
  )

  object Urls {
    val maxmindData  = "http://geolite.maxmind.com/download/geoip/database/GeoLiteCity.dat.gz"
  }

  object V {
    val maxmind      = "1.2.11"
    val collUtilsOld = "5.3.10"
    val collUtils    = "6.3.4"
    val specs2Old    = "1.12.4.1"
    val specs2       = "1.14"
  }

  object Libraries {
    val collUtilsOld = "com.twitter"                %  "util-collection"     % V.collUtilsOld
    val collUtils    = "com.twitter"                %% "util-collection"     % V.collUtils
    val maxmind      = "com.maxmind.geoip"          %  "geoip-api"           % V.maxmind
    val specs2Old    = "org.specs2"                 %% "specs2"              % V.specs2Old    % "test"
    val specs2       = "org.specs2"                 %% "specs2"              % V.specs2       % "test"
  }

  def onVersion[A](all: Seq[A] = Seq(), on292: => Seq[A] = Seq(), on210: => Seq[A] = Seq()) =
    scalaVersion(v => all ++ (if (v.contains("2.10")) on210 else on292))
}
