name := "conceptdiscovery"
organization := "org.clulab"

scalaVersion := "2.12.4"

resolvers += "Artifactory" at "http://artifactory.cs.arizona.edu:8081/artifactory/sbt-release"
resolvers ++= Seq(
  "jitpack" at "https://jitpack.io" // com.github.WorldModelers/Ontologies, com.github.jelmerk
)
libraryDependencies ++= {
  val procVer = "8.3.4"

  Seq(
    "org.clulab"    %% "processors-main"          % procVer,
    "org.clulab"    %% "processors-odin"          % procVer,
    "org.clulab"    %% "processors-openie"        % procVer,
    "org.scalatest" %% "scalatest"                % "3.0.1"  % Test,
    "com.typesafe"  %  "config"                   % "1.3.1",
    "org.slf4j"     % "slf4j-api"                 % "1.7.10",
    "org.jgrapht"   % "jgrapht-core"              % "1.4.0",
    "com.github.jelmerk"         %% "hnswlib-scala"           % "0.0.46"
  )
}

