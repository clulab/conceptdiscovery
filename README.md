[![Build Status](https://github.com/clulab/ConceptDiscovery/workflows/conceptdiscovery%20CI/badge.svg)](https://github.com/clulab/conceptdiscovery/actions)
[![Maven Central](https://img.shields.io/maven-central/v/org.clulab/conceptdiscovery_2.12?logo=apachemaven)](https://mvnrepository.com/artifact/org.clulab/conceptdiscovery)

# conceptdiscovery

We provide the concept discovery API here to help people to extract and rank the concepts in documents. The documents are ranked using an extension of
TextRank, an algorithm inspired by PageRank that treats text as a graph and applies a graph-based ranking algorithm to surface keywords or phrases.

An example usage is given below:

First you need to prepare a sequence of input sentences with sentence score, this score will later be used for filtering the sentences:
```
val texts = Seq(
    Seq(
      ("Food security is a measure of the availability of food and individuals' ability to access it.", 0.4),
      ))
```
Then convert texts to CDR documents:
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
We load the ConceptDiscoverer from the config file and apply it to the docuemtns:
```  
val conceptDiscovery = ConceptDiscoverer.fromConfig()
val concepts = conceptDiscovery.discoverConcepts(documents)
```
