package org.clulab.concepts

import org.clulab.processors.{Document, Processor, Sentence}
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.processors.clu.CluProcessor
import org.clulab.sequences.LexiconNER

class Annotator(processor: Processor, ner: LexiconNER) {

  val NER_OUTSIDE = "O"

  def annotate(text: String):Document = {
    val doc = processor.mkDocument(text, true)
    if (doc.sentences.nonEmpty) {
      if (processor.isInstanceOf[FastNLPProcessor]) {
        processor.tagPartsOfSpeech(doc)
        processor.lemmatize(doc)
      } else if (processor.isInstanceOf[CluProcessor]) {
        processor.lemmatize(doc)
        processor.tagPartsOfSpeech(doc)
      }
      processor.recognizeNamedEntities(doc)
      processor.parse(doc)
      processor.chunking(doc)
      // lexicon NER, in this case the list from the ontology examples
      // FIXME!
      doc.sentences foreach annotateSentence
      doc.clear()
      doc
    } else
      doc
  }

  /** Copied from Eidos GazetteerEntityFinder
   * Annotate the processors Sentence with found matches from the gazetteers in place.
   * The annotations will be placed in the `entities` field, using BIO notation (i.e., B-X, I-X, O, ...)
   * @param s Sentence to be annotated
   */
  def annotateSentence(s: Sentence): Unit = {
    // Find the gazetteer matches for the current sentence
    val matches = ner.find(s)

    // If a parser does not populate the entities field of a Sentence, just add whatever the gazetteers found
    if (s.entities.isEmpty)
      s.entities = Some(matches)
    // Otherwise, overwrite if we matched
    else {
      val sentenceEntities = s.entities.get

      matches.indices.foreach { index =>
        val matchTag = matches(index)
        // Check to see that there is a match
        if (matchTag != NER_OUTSIDE)
        // Overwrite the previous tag only if the gazetteers found something of interest
          sentenceEntities(index) = matchTag
      }
    }
  }

}
