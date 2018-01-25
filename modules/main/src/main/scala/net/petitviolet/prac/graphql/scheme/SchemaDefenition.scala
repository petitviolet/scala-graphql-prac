package net.petitviolet.prac.graphql.scheme

import java.util.concurrent.Executors

import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{ Todo, User }
import sangria.execution.deferred.{ DeferredResolver, Fetcher, Relation }
import sangria.macros.derive
import sangria.schema._

import scala.concurrent.{ ExecutionContext, Future }

object SchemaDefinition {
  private lazy val schemas: Seq[MySchema] = List(
    UserSchema,
    TodoSchema,
  )

  lazy val queryType: ObjectType[dao.container, Unit] = ObjectType.apply(
    "Query",
    fields[dao.container, Unit](
      schemas.map { _.asQueryField }: _*
    )
  )
  lazy val mutationType: Option[ObjectType[dao.container, Unit]] = {
    val mutationFields = schemas.flatMap { _.asMutationField }
    if (mutationFields.isEmpty) None
    else
      Option.apply {
        ObjectType.apply(
          "Mutation",
          fields[dao.container, Unit](
            mutationFields: _*
          )
        )
      }
  }
  lazy val resolver: DeferredResolver[dao.container] = DeferredResolver.fetchers(
    schemas.map { _.fetcher }: _*
  )

  lazy val schema = Schema.apply(queryType, mutationType)
}

sealed trait MySchema {
  def name: String

  def query: ObjectType[dao.container, Unit]

  def mutation: Option[ObjectType[dao.container, Unit]] = None

  def asQueryField: Field[dao.container, Unit] = Field(name, query, resolve = _ => ())

  def asMutationField: Option[Field[dao.container, Unit]] = mutation.map { m =>
    Field(name, m, resolve = _ => ())
  }

  def fetcher: Fetcher[dao.container, _, _, _]

  protected val threadNum: Int = 2
  protected implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadNum))
}

object TodoSchema extends MySchema {

  override def name = "todo"

  val _: Fetcher[dao.container, Todo, User, String] = {
    val byUser = Relation.apply[Todo, String]("byUser", { todo: Todo => todo.userId :: Nil })

    Fetcher.rel[dao.container, Todo, User, String](
      { (repo, ids) => Future.apply(repo.todoDao.findAllByIds(ids)) },
      { (repo, ids) => Future.apply {
        repo.userDao.findAllByIds(ids apply byUser)
      }}
    )
  }

  lazy val todoType = derive.deriveObjectType(
    derive.Interfaces[dao.container, Todo](entityType[dao.container]),
    derive.AddFields(
      Field("user", OptionType(UserSchema.userType), resolve = {
        ctx: Context[dao.container, Todo] =>
          DeferredValue(UserSchema.fetcher.defer(ctx.value.userId))
      })
    )
  )

  private object args {
    lazy val idArg = Argument("id", StringType, "id of todo")
    lazy val userIdArg = Argument("user_id", StringType, "id of user")
    lazy val titleArg = Argument("title", OptionInputType(StringType), "title of todo")
    lazy val descriptionArg =
      Argument("description", OptionInputType(StringType), "description of todo")
  }

  import args._

  override lazy val query = ObjectType(
    "TodoQuery",
    "TodoQuery",
    fields[dao.container, Unit](
      Field("all", ListType(todoType), arguments = Nil, resolve = { c =>
        c.ctx.todoDao.findAll
      }),
      Field(
        "search",
        ListType(todoType),
        arguments = userIdArg :: Nil,
        resolve = c => c.ctx.todoDao.findAllByUserId(c arg userIdArg)
      )
    )
  )

  override lazy val mutation = {
    Some(
      ObjectType(
        "TodoMutation",
        "TodoMutation",
        fields[dao.container, Unit](
          Field(
            "update",
            OptionType(todoType),
            arguments = idArg :: userIdArg :: titleArg :: descriptionArg :: Nil,
            resolve = { ctx =>
              ctx.ctx.todoDao.findById(ctx arg idArg).collect {
                case todo if todo.userId == ctx.arg(userIdArg) =>
                  val title = ctx.arg(titleArg) getOrElse todo.title
                  val description = ctx.arg(descriptionArg) getOrElse todo.description
                  val updatedTodo = todo.update(title, description)
                  ctx.ctx.todoDao.update(updatedTodo)
              }
            }
          )
        )
      ))
  }

  lazy val fetcher: Fetcher[dao.container, Todo, Todo, String] = Fetcher.caching {
    (ctx: dao.container, ids: Seq[String]) =>
      Future.apply {
        ctx.todoDao.findAllByIds(ids)
      }
  }
}

object UserSchema extends MySchema {
  override def name = "user"

  lazy val userType: ObjectType[Unit, User] = derive.deriveObjectType[Unit, User](
    derive.ObjectTypeDescription("user type"),
    derive.Interfaces[Unit, User](entityType[Unit]),
    derive.DocumentField("name", "name of user"),
    derive.RenameField("createdAt", "created_at"),
  )

  lazy val Id = Argument("id", StringType, "id of user")
  lazy val Email = Argument("email", OptionInputType(StringType), "email of user")

  lazy val query: ObjectType[dao.container, Unit] = ObjectType(
    "UserQuery",
    "user query(all/by_id/by_email)",
    fields[dao.container, Unit](
      Field(
        "all",
        ListType(userType),
        description = Some("list all users"),
        arguments = Nil,
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.userDao.findAll
        }
      ),
      Field(
        "by_id",
        OptionType(userType),
        description = Some("find by id"),
        arguments = Id :: Nil,
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.userDao.findById(c arg Id)
        }
      ),
      Field(
        "by_email",
        OptionType(userType),
        arguments = Email :: Nil,
        description = Some("find by email"),
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.userDao.findByEmail(c arg Email)
        }
      )
    )
  )

  lazy val fetcher: Fetcher[dao.container, User, User, String] = Fetcher.caching {
    (ctx: dao.container, ids: Seq[String]) =>
      Future.apply {
        ctx.userDao.findAllByIds(ids)
      }
  }

}
