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

object Dependencies {
  val resolutionRepos = Seq(
    "Scalaz Releases" at "https://dl.bintray.com/scalaz/releases/"
  )

  val maxmind   = "com.maxmind.geoip2" %  "geoip2"          % "2.11.0"
  val collUtils = "com.twitter"        %% "util-collection" % "18.2.0"
  val scalaz    = "org.scalaz"         %% "scalaz-core"     % "7.0.9"
  val specs2    = "org.specs2"         %% "specs2-core"     % "4.0.3"  % "test"
}
