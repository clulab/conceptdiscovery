package org.clulab.concepts

import com.github.jelmerk.knn.scalalike.Item

case class FlatOntologyAlignmentItem(id: FlatOntologyIdentifier, vector: Array[Float]) extends Item[FlatOntologyIdentifier, Array[Float]] {
  override def dimensions(): Int = vector.length
}
