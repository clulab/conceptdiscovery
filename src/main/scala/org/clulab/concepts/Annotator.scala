package org.clulab.concepts

import org.clulab.processors.{Document, Processor}
import org.clulab.processors.fastnlp.FastNLPProcessor
import org.clulab.processors.clu.CluProcessor

class Annotator(processor: Processor) {
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
      doc.clear()
      doc
    }else
      doc
  }
}
