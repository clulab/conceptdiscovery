package org.clulab.concepts

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.clulab.dynet.Utils
import org.clulab.embeddings.{CompactWordEmbeddingMap, WordEmbeddingMapPool}
import org.clulab.openie.entities.{CustomizableRuleBasedFinder, RuleBasedEntityFinder}
import org.clulab.openie.filtering.StopWordManager
import org.clulab.processors.Processor
import org.clulab.processors.clu.CluProcessor
import org.jgrapht.graph._
import org.jgrapht.alg.scoring.PageRank
import com.github.jelmerk.knn.scalalike.SearchResult
import java.util.Calendar
import java.util.concurrent.TimeUnit
import scala.collection.mutable

class ConceptDiscoverer(
   processor: Processor,
   entityFinder: RuleBasedEntityFinder,
   stopManager: StopWordManager,
   wordEmbeddings: CompactWordEmbeddingMap
 ) {

  Utils.initializeDyNet()

  /**
   * Discover concepts using the entity finder, keep track of where they were found,
   * and return the top K most frequent.  If a proportionSentencesKeep is provided,
   * then only concepts from the most salient sentences will be considered.
   * @param documents corpus
   * @param proportionSentencesKeep the proportion of sentences to consider, based on sentence
   *                                score
   * @param frequencyThreshold a concept must occur more than this many times in the corpus
   *                           to be kept
   * @param topK how many concepts to return
   * @return
   */
  def discoverMostFrequentConcepts(
                                    documents: Seq[DiscoveryDocument],
                                    proportionSentencesKeep: Option[Double],
                                    frequencyThreshold: Double,
                                    topK: Int = scala.Int.MaxValue
                                  ): Vector[Concept] = {
    discoverConcepts(documents, proportionSentencesKeep)
      // keep the concepts that have a frequency above the threshold and aren't a stop word
      .filter(c => c.frequency > frequencyThreshold && !stopManager.isStopWord(c.phrase))
      // most frequent first
      .sortBy(-_.frequency)
      .take(topK)
  }

  /**
   * Discover concepts using the entity finder, keep track of where they were found.
   * If a proportionSentencesKeep is provided, then only concepts from the most
   * salient sentences will be considered.
   * @param documents corpus
   * @param proportionSentencesKeep the proportion of sentences to consider, based on sentence
   *                                score
   * @return
   */
  def discoverConcepts(
                        documents: Seq[DiscoveryDocument],
                        proportionSentencesKeep: Option[Double] = None
                      ): Vector[Concept] = {
    if (proportionSentencesKeep.isDefined) {
      println()
    }
    // For each concept (key) holds the document locations (values) where that
    // concept occurred in the corpus.
    val conceptLocations = mutable.Map.empty[String, Set[DocumentLocation]]
      .withDefaultValue(Set.empty)
    var start = Calendar.getInstance
    var count = 1
    for (originalDoc <- documents) {
      println(s"doc ${originalDoc.docid} is being processed")
      val sentences = originalDoc.sentences
      val sentenceThreshold = proportionSentencesKeep.flatMap(prop => findThreshold(sentences, prop))

      sentences.zipWithIndex foreach { case (sentence, i) =>
        // see of the sentence's score is > threshold (else, if not using threshold)
        if (keepSentence(sentence, sentenceThreshold)) {
          // annotate this sentence
          val localDoc = processor.annotate(sentence.text)
          // find and collect concept mentions
          val mentions = entityFinder.extractAndFilter(localDoc)
          for (mention <- mentions) {
            conceptLocations(mention.text) += DocumentLocation(originalDoc.docid, i)
          }
        }
      }
      val time = Calendar.getInstance
      println(s"Finished in ${TimeUnit.MILLISECONDS.toSeconds(time.getTimeInMillis() - start.getTimeInMillis())} seconds, we already processed $count docs now.")
      start = Calendar.getInstance
      count += 1
    }

    conceptLocations.map{
      case (phrase, locations) => Concept(phrase, locations)
    }.toVector
  }

  /** Dynamically determine a threshold for the sentences */
  private def findThreshold(sentences: Seq[ScoredSentence], proportionKeep: Double): Option[Double] = {
    // if there's nothing to filter, or you want to keep everything, threshold inactive
    if (sentences.isEmpty || proportionKeep == 1.0) return None
    val numKeep = scala.math.ceil(sentences.length * proportionKeep).toInt
    val sorted = sentences.sortBy(-_.score)
    // the last valid score to keep, since we're using a >= criterion
    Some(sorted(numKeep - 1).score)
  }

  private def keepSentence(sentence: ScoredSentence, maybeThreshold: Option[Double]): Boolean = {
    if (sentence.text.isEmpty) false
    else if (maybeThreshold.isEmpty) true
    else sentence.score >= maybeThreshold.get
  }

  /**
   * Rank candidate concepts based on their overall saliency in the corpus.
   * First, the candidate concepts are pruned using
   * @param concepts all concepts to be ranked
   * @param similarityThreshold the threshold for the word embed similarity of the concepts,
   *                            word pairs whose similarity is = or below this will not
   *                            be included in the initial graph to be ranked
   * @return
   */
  def rankConcepts(concepts: Seq[Concept], similarityThreshold: Double = 0.0, topK: Int = 100): Seq[ScoredConcept] = {

    // construct graph from concepts
    val g = new SimpleWeightedGraph[String, DefaultEdge](classOf[DefaultEdge])
    for (concept <- concepts) {
      // add (internally library handles not adding if already there)
      g.addVertex(concept.phrase)
    }
//    for (List(c1, c2) <- concepts.combinations(2)) {
//      val weight = wordEmbeddings.avgSimilarity(c1.phrase.split(' '), c2.phrase.split(' '))
//      if (weight > similarityThreshold && !g.containsEdge(c1.phrase, c2.phrase)) {
//        val e = g.addEdge(c1.phrase, c2.phrase)
//        g.setEdgeWeight(e, weight)
//      }
//    }
    // build index
    val index = buildIndex(concepts)
    // build graph
    buildIndex(concepts).foreach{ c1 =>
      index.findNeighbors(c1.id, topK).foreach{case SearchResult(c2, distance) =>
        if (distance > similarityThreshold && !g.containsEdge(c1.id.nodeName, c2.id.nodeName)){
          val e = g.addEdge(c1.id.nodeName, c2.id.nodeName)
          g.setEdgeWeight(e, distance)
        }
      }
    }

    val pr = new PageRank(g)
    concepts
      // add PageRank scores to each concept
      .map(c => ScoredConcept(c, pr.getVertexScore(c.phrase)))
      // and return them in order of saliency (highest first)
      .sortBy(-_.saliency)
  }
  def readFlatOntologyItems(concepts: Seq[Concept]): Seq[FlatOntologyAlignmentItem] = {
    val namespace = "wm_flattened"

    val items: Seq[FlatOntologyAlignmentItem] = concepts.map { concept =>
      val name = concept.phrase
      val embedding = wordEmbeddings.makeCompositeVector(name.split(' '))
      val identifier = FlatOntologyIdentifier(namespace, name)

      FlatOntologyAlignmentItem(identifier, embedding)
    }

    items
  }

  def buildIndex(concepts: Seq[Concept]): FlatOntologyIndex.Index = {
    val items = readFlatOntologyItems(concepts)
    val index = FlatOntologyIndex.newIndex(items)

    index
  }


}

object ConceptDiscoverer {
  def fromConfig(config: Config = ConfigFactory.load()): ConceptDiscoverer = {
    val processor = new CluProcessor()
    val entityFinder = CustomizableRuleBasedFinder.fromConfig(
      config.withValue(
        "CustomRuleBasedEntityFinder.maxHops",
        ConfigValueFactory.fromAnyRef(0)
      )
    )
    val stopManager = StopWordManager.fromConfig(config)
    val embed_file_path: String = config.getString("glove.matrixResourceName")
    val wordEmbeddings = WordEmbeddingMapPool
      .getOrElseCreate(embed_file_path, compact = true)
      .asInstanceOf[CompactWordEmbeddingMap]

    new ConceptDiscoverer(processor, entityFinder, stopManager, wordEmbeddings)
  }
}