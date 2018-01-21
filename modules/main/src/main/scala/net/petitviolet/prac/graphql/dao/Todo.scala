package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Todo(id: Id,
                title: String,
                description: String,
                userId: Id,
                deadLine: ZonedDateTime,
                createdAt: ZonedDateTime,
                updatedAt: ZonedDateTime)
    extends Entity {
  def update(newTitle: String, newDescription: String): Todo = {
    copy(
      title = newTitle,
      description = newDescription,
      updatedAt = ZonedDateTime.now
    )
  }
}

object Todo {

  def create(userId: String, title: String, description: String): Todo = {
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

class TodoDao {
  import collection.mutable
  def now = ZonedDateTime.now()

  private val data: mutable.Map[Id, Todo] = mutable.Map(
    "todo-1" -> Todo(
      "todo-1",
      "title1!",
      "description1!",
      "user-1",
      now,
      now,
      now
    ),
    "todo-2" -> Todo(
      "todo-2",
      "title2!",
      "description2!",
      "user-1",
      now,
      now,
      now
    )
  )

  def findById(id: Id): Option[Todo] = data.get(id)

  def findAll: Seq[Todo] = data.values.toList

  def findAllByUserId(userId: Id): Seq[Todo] =
    data.collect {
      case (_, todo) if todo.userId == userId => todo
    }(collection.breakOut)

  def findAllByIds(ids: Seq[Id]): Seq[Todo] = ids collect data

  def create(todos: Todo): Todo = {
    data += (todos.id -> todos)
    todos
  }

  def update(todos: Todo): Todo = {
    data.update(
      todos.id,
      todos
    )
    todos
  }
}
