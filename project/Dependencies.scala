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
    "ScalaTools snapshots at Sonatype" at "https://oss.sonatype.org/content/repositories/snapshots/"
  )

  object V {
    val maxMind = "1.2.11"
    val specs2  = "3.9.5"
  }

  object Libraries {
    val maxMind = "com.maxmind.geoip" % "geoip-api" % V.maxMind
    object collUtils {
      val all  = "com.google.guava" % "guava" % "18.0"
    }
    val specs2  = Seq(
      "org.specs2" %% "specs2-core"  % V.specs2 % Test,
      "org.specs2" %% "specs2-junit" % V.specs2 % Test,
      "org.specs2" %% "specs2-mock"  % V.specs2 % Test
    )
  }

  def onVersion[A](all: Seq[A] = Nil, on211: => Seq[A] = Nil, on212: => Seq[A] = Nil) =
    scalaVersion(v => all ++ (if (v.contains("2.11.")) {
      on211
    } else {
      on212
    }))
}
