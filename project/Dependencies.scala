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
import sbt._

object Dependencies {
  val maxmind       = "com.maxmind.geoip2"    %  "geoip2"               % "2.15.0"
  val catsEffect    = "org.typelevel"         %% "cats-effect"          % "2.1.3"
  val cats          = "org.typelevel"         %% "cats-core"            % "2.1.1"
  val lruMap        = "com.snowplowanalytics" %% "scala-lru-map"        % "0.4.0"
  val scalaCheck    = "org.scalacheck"        %% "scalacheck"           % "1.14.3" % Test
  val specs2        = "org.specs2"            %% "specs2-core"          % "4.10.0"  % Test
}
