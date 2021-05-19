package org.clulab.concepts

import java.io.File

import com.github.jelmerk.knn.scalalike.floatCosineDistance
import com.github.jelmerk.knn.scalalike.SearchResult
import com.github.jelmerk.knn.scalalike.hnsw.HnswIndex

object FlatOntologyIndex {
  val dimensions = 300

  type Index = HnswIndex[FlatOntologyIdentifier, Array[Float], FlatOntologyAlignmentItem, Float]

  def load(filename: String): Index = {
    val index = HnswIndex.loadFromFile[FlatOntologyIdentifier, Array[Float], FlatOntologyAlignmentItem, Float](new File(filename))

    index.asInstanceOf[Index]
  }

  def newIndex(items: Iterable[FlatOntologyAlignmentItem]): Index = {
    val index = HnswIndex[FlatOntologyIdentifier, Array[Float], FlatOntologyAlignmentItem, Float](dimensions, floatCosineDistance, items.size)

    index.addAll(items)
    index
  }

  def findNearest(index: Index, vector: Array[Float]): Seq[SearchResult[FlatOntologyAlignmentItem, Float]] = {
    val maxHits = index.size

    findNearest(index, vector, maxHits, None)
  }

  def findNearest(index: Index, vector: Array[Float], maxHits: Int, thresholdOpt: Option[Float]): Seq[SearchResult[FlatOntologyAlignmentItem, Float]] = {
    val nearest = index.findNearest(vector, k = maxHits)
    val largest = nearest.map { case SearchResult(item, value) =>
      SearchResult(item, 1f - value)
    }
    val best = thresholdOpt.map { threshold =>
      largest.filter { case SearchResult(_, value) =>
        // If there is a comparison, then only use real numbers.  NaN satisfies no threshold.
        !value.isNaN && value >= threshold
      }
    }
      .getOrElse(largest)

    best
  }
}
