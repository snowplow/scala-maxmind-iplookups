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
    "ScalaTools snapshots at Sonatype" at "https://oss.sonatype.org/content/repositories/snapshots/",
    "Twitter Maven Repo" at "http://maven.twttr.com/" // For Twitter's util functions
  )

  object V {
    val maxmind = "1.2.11"
    object collUtils {
      val _29   = "5.3.10"
      val _210  = "6.3.4"
      val _211  = "6.12.1"     
    }
    object specs2 {
      val _29   = "1.12.4.1"
      val _210  = "1.14"
      val _211  = "2.3.13"
    }
  }

  object Libraries {
    val maxmind = "com.maxmind.geoip"          %  "geoip-api"            % V.maxmind
    object collUtils {
      val _29   = "com.twitter"                %  "util-collection"      % V.collUtils._29
      val _210  = "com.twitter"                %% "util-collection"      % V.collUtils._210
      // Not yet released
      val _211  = "com.twitter"                %% "util-collection"      % V.collUtils._211
    }
    object specs2 {
      val _29   = "org.specs2"                 %% "specs2"               % V.specs2._29        % "test"
      val _210  = "org.specs2"                 %% "specs2"               % V.specs2._210       % "test"
      val _211  = "org.specs2"                 %% "specs2"               % V.specs2._211       % "test"
    }
  }

  def onVersion[A](all: Seq[A] = Seq(), on29: => Seq[A] = Seq(), on210: => Seq[A] = Seq(), on211: => Seq[A] = Seq()) =
    scalaVersion(v => all ++ (if (v.contains("2.9.")) {
      on29
    } else if (v.contains("2.10.")) {
      on210
    } else {
      on211
    }))
}
