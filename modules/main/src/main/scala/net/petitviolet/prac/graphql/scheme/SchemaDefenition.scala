package net.petitviolet.prac.graphql.scheme

import java.time.format.DateTimeFormatter

import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{Todos, Users}
import sangria.execution.deferred.{Fetcher, HasId}
import sangria.schema._

import scala.concurrent.Future

object SchemaDefinition {
  val schemas: Seq[MySchema] = List(
    UserSchema,
    TodoSchema,
  )

  val queryType = ObjectType.apply(
    "Query",
    fields[dao.container, Unit](
      schemas.map {_.asField}: _*
    )
  )
  val query = Schema.apply(queryType)
}

trait MySchema {
  def name: String
  def query: ObjectType[dao.container, Unit]
  def asField: Field[dao.container, Unit] = Field(name,  query, resolve = _ => ())
}

object TodoSchema extends MySchema {

  override def name = "todo"

  val todoType: ObjectType[dao.container, Todos] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:SS")
    ObjectType(
      "Todo",
      fields[dao.container, Todos](
        Field("id", StringType, resolve = _.value.id),
        Field("name", StringType, resolve = _.value.title),
        Field("description", StringType, resolve = _.value.description),
        Field("deadline", StringType, resolve = _.value.deadLine.format(formatter)),
        Field("user", OptionType(UserSchema.userType), resolve = { ctx =>
          ctx.ctx.usersDao.findById(ctx.value.userId)
        })
      )
    )
  }
  val userId = Argument("user_id", StringType, "id of user")

  val query = ObjectType(
    "TodoQuery",
    "TodoQuery",
    fields[dao.container, Unit](
      Field("all", ListType(todoType),
        arguments = Nil,
        resolve = { c => c.ctx.todoDao.findAll }
      ),
      Field("search", ListType(todoType),
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

object UserSchema extends MySchema {
  override def name = "user"

  val userType: ObjectType[Unit, Users] = ObjectType(
    "User",
    "user type",
    fields[Unit, Users](
      Field("id", StringType, resolve = _.value.id),
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
    )
  )

  val Id = Argument("id", StringType, "id of user")
  val Email = Argument("email", OptionInputType(StringType), "email of user")

  val query = ObjectType(
    "UserQuery",
    "user query(all/by_id/by_email)",
    fields[dao.container, Unit](
      Field("all", ListType(userType),
        description = Some("list all users"),
        arguments = Nil,
        resolve = { c => c.ctx.usersDao.findAll }
      ),
      Field("by_id", OptionType(userType),
        description = Some("find by id"),
        arguments = Id :: Nil,
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.usersDao.findById(c arg Id)
        }
      ),
      Field("by_email", OptionType(userType),
        arguments = Email :: Nil,
        description = Some("find by email"),
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.usersDao.findByEmail(c arg Email)
        }
      )
    )
  )

  val fetcher = Fetcher.caching({ (ctx: dao.container, ids: Seq[String]) =>
    Future.successful {
      ctx.usersDao.findAllByIds(ids)
    }
  })(HasId(_.id))

}
