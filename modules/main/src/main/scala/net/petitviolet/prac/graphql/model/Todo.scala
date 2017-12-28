package net.petitviolet.prac.graphql.model

import java.time.ZonedDateTime

case class Todo(id: Id[Todo], title: Todo.Title,
                description: Todo.Description, deadline: Todo.Deadline,
                status: Todo.Status) extends Entity {

}

object Todo {
  case class Title(value: String) extends AnyVal
  case class Description(value: String) extends AnyVal
  case class Deadline(value: ZonedDateTime) extends AnyVal

  sealed abstract class Status(value: Int)
  case object Status {
    case object Completed extends Status(1)
    case object UnCompleted extends Status(2)
  }
}
