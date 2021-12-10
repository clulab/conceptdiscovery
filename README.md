[![Build Status](https://github.com/clulab/ConceptDiscovery/workflows/conceptdiscovery%20CI/badge.svg)](https://github.com/clulab/conceptdiscovery/actions)
[![Maven Central](https://img.shields.io/maven-central/v/org.clulab/conceptdiscovery_2.12?logo=apachemaven)](https://mvnrepository.com/artifact/org.clulab/conceptdiscovery)

# conceptdiscovery

This repository contains code to identify salient concepts in a text corpus. This code is part of the World Modeler's Ontology in a Day (OIAD) pipeline.

At a high level, this software uses the [TextRank algorithm](https://aclanthology.org/W04-3252.pdf) to rank noun phrases rather than sentences, as the original algorithm did. More specifically, the algorithm constructs a graph where concepts, i.e., noun phrases, are nodes, and edges indicate two concepts with high similar score. Then, the TextRank algorithm is used to generate PageRank scores for all nodes in the graph. The top nodes with the highest scores are returned by the algorithm.

The API follows the following steps.

First you need to prepare a sequence of input sentences, with each sentence associated with a score, where TODO ZHENG: WHAT DO THESE SCORES MEAN AND WHERE DO THEY COME FROM?. This score is used for filtering out less important sentences:
```
val texts = Seq(
    Seq(
      ("Food security is a measure of the availability of food and individuals' ability to access it.", 0.4),
      ))
```
Then convert texts to the World Modelers CDR document format:
```
  val documents = for ((sentencesWithScores, i) <- texts.zipWithIndex) yield {
    var end = 0
    val scoredSentences = for ((sentence, sentenceScore) <- sentencesWithScores) yield {
      val start = end
      end = start + sentence.length
      ScoredSentence(sentence, start, end, sentenceScore)
    }
    DiscoveryDocument(s"doc$i", scoredSentences)
  }
```
We load the ConceptDiscoverer from the config file and apply it to the documents:
```  
val conceptDiscovery = ConceptDiscoverer.fromConfig()
val concepts = conceptDiscovery.discoverConcepts(documents)
val rankedConcepts = conceptDiscovery.rankConcepts(concepts)
```

You can save the ranked concepts in json formart:
```
val conceptSink = new ConceptSink(rankedConcepts)
Console.withOut(new PrintStream(new FileOutputStream("output_full.json"))){
   conceptSink.printJson()
}
```
Here is some sample outputs:
```
[ {
  "concept" : {
    "phrase" : "' average production",
    "locations" : [ {
      "document_id" : "0df84c35985ba0130636ab8686943756",
      "sentence_index" : 225
    }, {
      "document_id" : "0df84c35985ba0130636ab8686943756",
      "sentence_index" : 244
    }, ... ]
  },
  "saliency" : 0.07536057667010426
}, {
  "concept" : {
    "phrase" : "'s production",
    "locations" : [ {
      "document_id" : "0df84c35985ba0130636ab8686943756",
      "sentence_index" : 225
    }, {
      "document_id" : "0df84c35985ba0130636ab8686943756",
      "sentence_index" : 244
    }, ... ]
  },
  "saliency" : 0.07435705153994535
}, {
  "concept" : {
    "phrase" : "women",
    "locations" : [ {
      "document_id" : "0289d3a06c7872344154991549c6f823",
      "sentence_index" : 10
    }, {
      "document_id" : "0289d3a06c7872344154991549c6f823",
      "sentence_index" : 11
    }, ... ]
  },
  "saliency" : 0.07011902079604163
}, {
  "concept" : {
    "phrase" : "Somalia",
    "locations" : [ {
      "document_id" : "0bc9c72b3c259d67672e5c3163101828",
      "sentence_index" : 5
    }, {
      "document_id" : "0194254586b5e82c3b24af36907b94d1",
      "sentence_index" : 9
    }, {
      "document_id" : "0eb5eee25d3e3f652fd707a0a674a38b",
      "sentence_index" : 11
    }, ... ]
  },
  "saliency" : 0.06664052798844469
}, ... ]

```
