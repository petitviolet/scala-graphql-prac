package net.petitviolet.prac.graphql

import java.time.{ ZoneId, ZonedDateTime }
import java.util.UUID

package object dao {
  type Id = String
  trait Entity {
    def id: Id
  }
  def generateId: Id = UUID.randomUUID().toString
  private val zoneId = ZoneId.of("Asia/Tokyo")
  def now(): ZonedDateTime = ZonedDateTime.now(zoneId)
}
