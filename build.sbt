import org.clulab.sbt.Resolvers

name := "conceptdiscovery"
description := BuildUtils.singleLine("""
  |This project is  used to discover concepts.
""")

// Last checked 2021-12-06
val scala11 = "2.11.12" // up to 2.11.12
val scala12 = "2.12.15" // up to 2.12.15
val scala13 = "2.13.7"  // up to 2.13.7

// Processors is not available for scala13, so it is skipped here.
ThisBuild / crossScalaVersions := Seq(scala12, scala11) // , scala13)
ThisBuild / scalaVersion := crossScalaVersions.value.head

resolvers ++= Seq(
  Resolvers.clulabResolver, // org.clulab/glove-840b-300d
  Resolvers.jitpackResolver
)

libraryDependencies ++= {
  val procVer = "8.3.4"

  Seq(
    "org.clulab"         %% "processors-main"          % procVer,
    "org.clulab"         %% "processors-odin"          % procVer,
    "org.clulab"         %% "processors-openie"        % procVer,
    "org.clulab"         %% "processors-corenlp"       % procVer,
    "org.scalatest"      %% "scalatest"                % "3.0.1"  % Test,
    "com.typesafe"       %  "config"                   % "1.3.1",
    "org.slf4j"          % "slf4j-api"                 % "1.7.10",
    "org.jgrapht"        % "jgrapht-core"              % "1.4.0",
    "com.github.jelmerk" %% "hnswlib-scala"            % "0.0.46"
  )
}


