package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Todos(id: Id, title: String, description: String, userId: Id,
                 deadLine: ZonedDateTime, createdAt: ZonedDateTime, updatedAt: ZonedDateTime)

class TodosDao {
  import collection.mutable
  def now = ZonedDateTime.now()

  private val data: mutable.Map[Id, Todos] = mutable.Map(
    "todo-1" -> Todos("todo-1", "title!", "description!", "hello", now, now, now)
  )

  def findById(id: Id): Option[Todos] = data.get(id)

  def findAll: Seq[Todos] = data.values.toList

  def findAllByUserId(userId: Id): Seq[Todos] = data.collect {
    case (_, todo) if todo.userId == userId => todo
  }(collection.breakOut)

  def findAllByIds(ids: Seq[Id]): Seq[Todos] = ids collect data

  def create(users: Todos): Unit = data += (users.id -> users)

  def update(users: Todos): Unit = data.update(users.id, users)
}

