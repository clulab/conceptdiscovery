package org.clulab.concepts

@SerialVersionUID(1L)
case class FlatOntologyIdentifier(ontologyName: String, nodeName: String) extends Identifier {

  override def toString(): String = s"$ontologyName//$nodeName"
}
