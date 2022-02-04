package org.clulab.concepts.apps

import org.clulab.concepts.{ConceptDiscoverer, ConceptSink, ConceptSource, DiscoveryDocument}

import org.json4s.jackson.JsonMethods

import java.io.{FileOutputStream, PrintStream}

object GraphRankingApp extends App {

  val input_file = "sample_graph.json"
  val rankedConcepts = ConceptDiscoverer.rankGraphFromJSON(input_file)
  val json = JsonMethods.pretty(rankedConcepts)


  Console.withOut(new PrintStream(new FileOutputStream("output_sample.json"))){
    println(json)
  }
}