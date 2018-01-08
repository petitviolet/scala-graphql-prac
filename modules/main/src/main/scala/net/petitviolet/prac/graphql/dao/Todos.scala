package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Todos(id: Id,
                 title: String,
                 description: String,
                 userId: Id,
                 deadLine: ZonedDateTime,
                 createdAt: ZonedDateTime,
                 updatedAt: ZonedDateTime)
    extends Entity {
  def update(newTitle: String, newDescription: String): Todos = {
    copy(
      title = newTitle,
      description = newDescription,
      updatedAt = ZonedDateTime.now
    )
  }
}

object Todos {

  def create(userId: String, title: String, description: String): Todos = {
    val now = ZonedDateTime.now()
    apply(
      generateId,
      title,
      description,
      userId,
      now,
      now,
      now
    )
  }

}

class TodosDao {
  import collection.mutable
  def now = ZonedDateTime.now()

  private val data: mutable.Map[Id, Todos] = mutable.Map(
    "todo-1" -> Todos(
      "todo-1",
      "title1!",
      "description1!",
      "user-1",
      now,
      now,
      now
    ),
    "todo-2" -> Todos(
      "todo-2",
      "title2!",
      "description2!",
      "user-1",
      now,
      now,
      now
    )
  )

  def findById(id: Id): Option[Todos] = data.get(id)

  def findAll: Seq[Todos] = data.values.toList

  def findAllByUserId(userId: Id): Seq[Todos] =
    data.collect {
      case (_, todo) if todo.userId == userId => todo
    }(collection.breakOut)

  def findAllByIds(ids: Seq[Id]): Seq[Todos] = ids collect data

  def create(todos: Todos): Todos = {
    data += (todos.id -> todos)
    todos
  }

  def update(todos: Todos): Todos = {
    data.update(
      todos.id,
      todos
    )
    todos
  }
}
