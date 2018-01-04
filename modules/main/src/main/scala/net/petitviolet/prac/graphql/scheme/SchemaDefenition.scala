package net.petitviolet.prac.graphql.scheme

import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{Todos, Users}
import sangria.execution.deferred.{DeferredResolver, Fetcher, HasId}
import sangria.schema._

import scala.concurrent.{ExecutionContext, Future}

object SchemaDefinition {
  lazy val schemas: Seq[MySchema] = List(
    UserSchema,
    TodoSchema,
  )

  lazy val queryType = ObjectType.apply(
    "Query",
    fields[dao.container, Unit](
      schemas.map {_.asField}: _*
    )
  )
  lazy val resolver: DeferredResolver[dao.container] = DeferredResolver.fetchers(schemas.map { _.fetcher }: _*)

  lazy val query = Schema.apply(queryType)
}

sealed trait MySchema {
  def name: String
  def query: ObjectType[dao.container, Unit]
  def asField: Field[dao.container, Unit] = Field(name,  query, resolve = _ => ())
  def fetcher: Fetcher[dao.container, _, _, _]

  protected implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))
}

object TodoSchema extends MySchema {

  override def name = "todo"

  lazy val todoType: ObjectType[dao.container, Todos] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:SS")
    ObjectType(
      "Todo",
      fields[dao.container, Todos](
        Field("id", StringType, resolve = _.value.id),
        Field("name", StringType, resolve = _.value.title),
        Field("description", StringType, resolve = _.value.description),
        Field("deadline", StringType, resolve = _.value.deadLine.format(formatter)),
        Field("user", OptionType(UserSchema.userType), resolve = { ctx =>
          UserSchema.fetcher.defer(ctx.value.userId)
        })
      )
    )
  }
  lazy val userId = Argument("user_id", StringType, "id of user")

  lazy val query = ObjectType(
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

  lazy val fetcher: Fetcher[dao.container, Todos, Todos, String] = Fetcher.caching {
    (ctx: dao.container, ids: Seq[String]) =>
      Future.apply {
        ctx.todoDao.findAllByIds(ids)
      }
  }(HasId(_.id))
}

object UserSchema extends MySchema {
  override def name = "user"

  lazy val userType: ObjectType[Unit, Users] = ObjectType(
    "User",
    "user type",
    fields[Unit, Users](
      Field("id", StringType, description = Some("id of user"), resolve = _.value.id),
      Field("name", StringType, description = Some("name of user"), resolve = _.value.name),
      Field("email", StringType, description = Some("email of user"), resolve = _.value.email),
    )
  )

  lazy val Id = Argument("id", StringType, "id of user")
  lazy val Email = Argument("email", OptionInputType(StringType), "email of user")

  lazy val query = ObjectType(
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

  lazy val fetcher: Fetcher[dao.container, Users, Users, String] = Fetcher.caching {
    (ctx: dao.container, ids: Seq[String]) =>
      Future.apply {
        ctx.usersDao.findAllByIds(ids)
      }
  }(HasId(_.id))

}
