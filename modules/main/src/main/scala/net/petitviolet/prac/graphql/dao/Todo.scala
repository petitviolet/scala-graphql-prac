package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

import spray.json.JsonFormat

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
      updatedAt = now()
    )
  }
}

object Todo {

  def create(userId: String, title: String, description: String): Todo = {
    val dateTime = now()
    apply(
      generateId,
      title,
      description,
      userId,
      dateTime,
      dateTime,
      dateTime
    )
  }

  import spray.json._

  val todoJsonFormat: RootJsonFormat[Todo] = DefaultJsonProtocol.jsonFormat7(Todo.apply)
}

class TodoDao extends RedisDao[Todo] {

  override protected val prefix: String = "todo"
  override protected implicit val jsonFormat: JsonFormat[Todo] = Todo.todoJsonFormat

  def findAllByUserId(userId: Id): Seq[Todo] =
    findAll.filter { _.userId == userId }
}
