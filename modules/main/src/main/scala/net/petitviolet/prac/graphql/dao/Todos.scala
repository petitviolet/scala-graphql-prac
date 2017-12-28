package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Todos(id: Id, title: String, description: String,
                 deadLine: ZonedDateTime, createdAt: ZonedDateTime, updatedAt: ZonedDateTime)

object TodosDao {
  import collection.mutable
  private val data: mutable.Map[Id, Todos] = mutable.Map.empty

  def findById(id: Id): Option[Todos] = data.get(id)

  def create(users: Todos): Unit = data += (users.id -> users)

  def update(users: Todos): Unit = data.update(users.id, users)
}

