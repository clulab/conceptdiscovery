package org.clulab.sbt

import sbt._ // at

object Resolvers {
  val clulabResolver = ("Artifactory" at "http://artifactory.cs.arizona.edu:8081/artifactory/sbt-release").withAllowInsecureProtocol(true)
  val jitpackResolver = "jitpack" at "https://jitpack.io"
}

