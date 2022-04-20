package org.clulab.concepts

import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.clulab.dynet.Utils
import org.clulab.embeddings.{CompactWordEmbeddingMap, WordEmbeddingMapPool}
import org.clulab.openie.entities.{CustomizableRuleBasedFinder, RuleBasedEntityFinder}
import org.clulab.openie.filtering.StopWordManager
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.utils.Closer.AutoCloser
import org.jgrapht.graph._
import org.jgrapht.alg.scoring.PageRank
import com.github.jelmerk.knn.scalalike.SearchResult

import java.util.Calendar
import java.util.concurrent.TimeUnit
import scala.collection.mutable
import java.io.{File, FileOutputStream, PrintStream}
import org.clulab.sequences.LexiconNER
import org.clulab.utils.{Logging, Sourcer}
import org.json4s.JArray
import org.json4s.JValue
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods
import org.slf4j.{Logger, LoggerFactory}

import scala.io.Source
import scala.util.parsing.json._

class ConceptDiscoverer(
   annotator: Annotator,
   entityFinder: RuleBasedEntityFinder,
   stopManager: StopWordManager,
   wordEmbeddings: CompactWordEmbeddingMap
 ) {

  val logger: Logger = LoggerFactory.getLogger(classOf[ConceptDiscoverer])

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
  ): Vector[Concept] =
{

    // For each concept (key) holds the document locations (values) where that
    // concept occurred in the corpus.
    val conceptLocations = mutable.Map.empty[String, Set[DocumentLocation]]
      .withDefaultValue(Set.empty)

    documents.zipWithIndex.foreach { case (originalDoc, docIndex) =>
      logger.info(s"$docIndex doc ${originalDoc.docid} is being processed")
      val start = Calendar.getInstance
      val sentences = originalDoc.sentences
      val sentenceThreshold = proportionSentencesKeep.flatMap(prop => findThreshold(sentences, prop))

      sentences.zipWithIndex.par.foreach { case (sentence, sentIndex) =>
        // see of the sentence's score is > threshold (else, if not using threshold)
        if (keepSentence(sentence, sentenceThreshold)) {
          // annotate this sentence
          val localDoc = annotator.annotate(sentence.text)
          // find and collect concept mentions
          val mentions = entityFinder.extractAndFilter(localDoc)
          if (mentions.nonEmpty) conceptLocations.synchronized {
            for (mention <- mentions) {
              conceptLocations(mention.text) += DocumentLocation(originalDoc.docid, sentIndex)
            }
          }
        }
      }
      val time = Calendar.getInstance
      logger.info(s"Finished in ${TimeUnit.MILLISECONDS.toSeconds(time.getTimeInMillis() - start.getTimeInMillis())} seconds, we already processed ${docIndex + 1} docs now.")
    }

    conceptLocations.map{
      case (phrase, locations) => Concept(phrase, locations)
    }.toVector
  }

  def saveConcepts(concepts: Vector[Concept], saved_loc: String): Unit={
    Console.withOut(new PrintStream(new FileOutputStream(saved_loc))) {
      concepts.foreach { concept =>
        val phrase = concept.phrase
        val locations = concept.documentLocations
        locations.foreach{loc =>
          logger.info(phrase+'\t'+loc.docid+' '+loc.sent.toString)
        }
      }
    }
  }

  def loadConcepts(file_loc: String): Vector[Concept]={
    val conceptLocations = mutable.Map.empty[String, Set[DocumentLocation]]
      .withDefaultValue(Set.empty)

    Sourcer.sourceFromFile(new File(file_loc)).autoClose { source =>
      val lines = source.getLines().toArray
      lines.foreach { line =>
        val Array(phrase, locations) = line.stripLineEnd.split('\t').take(2)
        val Array(docid, sent) = locations.split(' ').take(2)
        conceptLocations(phrase) += DocumentLocation(docid, sent.toInt)
      }
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

    // build index
    val index = buildIndex(concepts)
    // build graph
    index.foreach{ c1 =>
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
  /**
   * Rank candidate concepts based on their overall saliency in the corpus.
   * First, the candidate concepts are pruned using
   * @param input_file the input JSON file, contains the information of a graph
   * @return
   */
  def rankGraphFromJSON(input_file: String): JArray = {
    var concepts = Seq[Concept]()
    var node_map = Map[String, String]()
    var node_map_rev = Map[String, String]()
    val locations = Set(DocumentLocation(input_file, 0))
    val jsonString = Source.fromFile(input_file).getLines.mkString
    val parsed = JSON.parseFull(jsonString)
    val nodes = parsed.get.asInstanceOf[Map[String, Any]]("nodes").asInstanceOf[List[Any]]
    val edges = parsed.get.asInstanceOf[Map[String, Any]]("edges").asInstanceOf[List[Any]]
    val directed = parsed.get.asInstanceOf[Map[String, Any]]("directed").asInstanceOf[Boolean]
    // construct graph from concepts
    if (directed){
      val g = new SimpleDirectedWeightedGraph[String, DefaultEdge](classOf[DefaultEdge])
    }else{
      val g = new SimpleWeightedGraph[String, DefaultEdge](classOf[DefaultEdge])
    }
    val g =  if (directed) {new SimpleDirectedWeightedGraph[String, DefaultEdge](classOf[DefaultEdge])} else  {new SimpleWeightedGraph[String, DefaultEdge](classOf[DefaultEdge])}
    for (node <- nodes) {
      // add (internally library handles not adding if already there)
      g.addVertex(node.asInstanceOf[Map[String, String]]("text"))
      node_map += (node.asInstanceOf[Map[String, String]]("id") -> node.asInstanceOf[Map[String, String]]("text"))
      node_map_rev += (node.asInstanceOf[Map[String, String]]("text") -> node.asInstanceOf[Map[String, String]]("id"))
      concepts :+= Concept(node.asInstanceOf[Map[String, String]]("text"), locations)
    }
    for (edge <- edges){
      val weight = edge.asInstanceOf[Map[String, Double]]("weight")
      val src = node_map(edge.asInstanceOf[Map[String, String]]("src"))
      val dst = node_map(edge.asInstanceOf[Map[String, String]]("dst"))
      if (!g.containsEdge(src,dst)){
        val e = g.addEdge(src, dst)
        g.setEdgeWeight(e, weight)
      }else if (g.containsEdge(src,dst)){
        // If there are multiple edges between two nodes, we add the two edge weights up as the new weight
        val e = g.getEdge(src,dst)
        g.setEdgeWeight(e, weight + g.getEdgeWeight(e))
      }
    }

    val pr = new PageRank(g)
    val scorededConcepts = concepts
      // add PageRank scores to each concept
      .map(c => ScoredConcept(c, pr.getVertexScore(c.phrase)))
      // and return them in order of saliency (highest first)
      .sortBy(-_.saliency)

    new JArray(
      scorededConcepts.toList.map { scoredConcept =>
        ("concept" ->
          ("text" -> scoredConcept.concept.phrase)~
          ("id" -> node_map_rev(scoredConcept.concept.phrase))
          ) ~
          ("saliency" -> scoredConcept.saliency)
      }
    )
  }
  def fromConfig(config: Config = ConfigFactory.load()): ConceptDiscoverer = {
    Utils.initializeDyNet()
    val processor = new FastNLPProcessor()
    val lexiconNER = LexiconNER(kbs = Seq("org/clulab/concepts/CurrExamples.tsv"), caseInsensitiveMatching = false)
    val annotator = new Annotator(processor, lexiconNER)
    // Without this priming, the processor will hand.
    annotator.annotate("Once upon a time there were three bears.")
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

    new ConceptDiscoverer(annotator, entityFinder, stopManager, wordEmbeddings)
  }
}