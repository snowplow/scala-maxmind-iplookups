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
