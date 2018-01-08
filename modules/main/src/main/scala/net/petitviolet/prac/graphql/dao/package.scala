package net.petitviolet.prac.graphql

import java.util.UUID

package object dao {
  type Id = String
  trait Entity {
    def id: Id
  }
  def generateId: Id = UUID.randomUUID().toString
}
