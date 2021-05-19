package org.clulab.concepts.apps

import org.clulab.concepts.{ConceptDiscoverer, ConceptSink, ConceptSource, DiscoveryDocument}
import org.clulab.utils.FileUtils

object DiscoverAndRankConceptsApp extends App {
  val inputDir = args(0)
  val thresholdFrequency = args(1).toDouble
  val thresholdSimilarity = args(2).toDouble
  val topPick = args(3).toInt
  // This goes last, even though not used last, because it is optional.
  val sentenceThresholdOpt = args.lift(4).map(_.toDouble)

  val conceptDiscovery = ConceptDiscoverer.fromConfig()
  val files = FileUtils.findFiles(inputDir, "json").take(10)
  val discoveryDocuments = files.flatMap { file =>
    val conceptSource = ConceptSource(file)
    val docId = conceptSource.getIdOpt.get
    val scoredSentences = conceptSource.getScoredSentences

    // Things elsewhere seem to require at least some text and scored sentences.
    if (conceptSource.text.nonEmpty && scoredSentences.nonEmpty)
      Some(DiscoveryDocument(docId, scoredSentences))
    else
      None
  }
  val concepts = conceptDiscovery.discoverMostFrequentConcepts(discoveryDocuments, sentenceThresholdOpt, thresholdFrequency, topPick)
  val rankedConcepts = conceptDiscovery.rankConcepts(concepts, thresholdSimilarity)
  val conceptSink = new ConceptSink(rankedConcepts)

  conceptSink.printJson()
}
