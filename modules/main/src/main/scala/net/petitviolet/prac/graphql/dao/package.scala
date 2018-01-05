package net.petitviolet.prac.graphql

import java.util.UUID

package object dao {
  type Id = String
  def generateId: Id = UUID.randomUUID().toString
}
