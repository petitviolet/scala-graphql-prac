package net.petitviolet.prac.graphql.scheme

import java.time.format.DateTimeFormatter

import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{Todos, Users}
import sangria.execution.deferred.{Fetcher, HasId}
import sangria.schema._

import scala.concurrent.Future

object SchemaDefinition {
  val queryType = ObjectType.apply(
    "Query",
    fields[dao.container, Unit](
      Field("user_query", UserSchema.query, resolve = _ => ()),
      Field("todo_query", TodoSchema.query, resolve = _ => ()),
    )
  )
  val query = Schema.apply(queryType)
}


object TodoSchema {
  val todoType: ObjectType[Unit, Todos] = ObjectType(
    "Todo",
    fields[Unit, Todos](
      Field("id", StringType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.title),
      Field("description", StringType, resolve = _.value.description),
      Field("deadline", StringType, resolve = _.value.deadLine.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:SS"))),
    )
  )
  val userId = Argument("user_id", StringType, "id of user")

  val query = ObjectType(
    "TodoQuery",
    fields[dao.container, Unit](
      Field("todos", ListType(todoType),
        arguments = Nil,
        resolve = { c => c.ctx.todoDao.findAll }
      ),
      Field("todo", ListType(todoType),
        arguments = userId :: Nil,
        resolve = c => c.ctx.todoDao.findAllByUserId(c arg userId)
      )
    )
  )

  val fetcher = Fetcher.caching({ (ctx: dao.container, ids: Seq[String]) =>
    Future.successful {
      ctx.todoDao.findAllByIds(ids)
    }
  })(HasId(_.id))
}

object UserSchema {
  val userType: ObjectType[Unit, Users] = ObjectType(
    "User",
    fields[Unit, Users](
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
    )
  )

  val Id = Argument("id", StringType, "id of user")

  val query = ObjectType(
    "UserQuery",
    fields[dao.container, Unit](
      Field("users", ListType(userType),
        arguments = Nil,
        resolve = { c => c.ctx.usersDao.findAll }
      ),
      Field("user", OptionType(userType),
        arguments = Id :: Nil,
        resolve = c => c.ctx.usersDao.findById(c arg Id)
      )
    )
  )

  val fetcher = Fetcher.caching({ (ctx: dao.container, ids: Seq[String]) =>
    Future.successful {
      ctx.usersDao.findAllByIds(ids)
    }
  })(HasId(_.id))

}
