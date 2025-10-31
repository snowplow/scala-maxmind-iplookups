addSbtPlugin("com.github.sbt"            % "sbt-ci-release" % "1.11.2")
addSbtPlugin("org.scoverage"             % "sbt-coveralls"  % "1.3.2")
addSbtPlugin("org.scalameta"             % "sbt-scalafmt"   % "2.4.6")
addSbtPlugin("org.scoverage"             % "sbt-scoverage"  % "2.4.0")
addSbtPlugin("com.typesafe.sbt"          % "sbt-site"       % "1.4.1")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat"   % "0.4.1")

libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
