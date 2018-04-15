package net.petitviolet.prac.graphql.scheme

import java.util.concurrent.Executors

import net.petitviolet.operator.toPipe
import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{ AuthnException, Id, Todo, User }
import sangria.execution.FieldTag
import sangria.execution.deferred.{ DeferredResolver, Fetcher, Relation }
import sangria.macros.derive
import sangria.schema._

import scala.concurrent.{ ExecutionContext, Future }

object SchemaDefinition {
  case object Authenticated extends FieldTag

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
  protected def log(msg: => String): Unit = println(s"[$name]$msg")

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
    val byUser = Relation.apply[Todo, String]("byUser", { todo: Todo =>
      todo.userId :: Nil
    })

    Fetcher.rel[dao.container, Todo, User, String](
      { (repo, ids) =>
        Future.apply(repo.todoDao.findAllByIds(ids))
      }, { (repo, ids) =>
        Future.apply {
          repo.userDao.findAllByIds(ids apply byUser)
        }
      }
    )
  }

  lazy val todoType: ObjectType[dao.container, Todo] = derive.deriveObjectType(
    derive.Interfaces[dao.container, Todo](entityType[dao.container]),
    derive.AddFields(
      Field("user", OptionType(UserSchema.userType), resolve = {
        ctx: Context[dao.container, Todo] =>
          // cause N+1 problem
          // ctx.ctx.userDao.findById(ctx.value.userId)
          DeferredValue(UserSchema.fetcher.defer(ctx.value.userId))
      })
    )
  )

  private object args {
    val id = Argument("id", StringType, "id of todo")
    val userId = Argument("user_id", StringType, "id of user")
    val titleOpt = Argument("title", OptionInputType(StringType), "title of todo")
    val descriptionOpt = Argument("description", OptionInputType(StringType), "description of todo")

    val title = Argument("title", StringType, "title of todo")
    val description = Argument("description", StringType, "description of todo")
    val deadlineArg = Argument("deadline", StringType, "deadline of todo")
  }

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
        arguments = args.userId :: Nil,
        resolve = c => c.ctx.todoDao.findAllByUserId(c arg args.userId)
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
            "create",
            todoType,
            arguments = args.userId :: args.title :: args.description :: Nil,
            resolve = { ctx =>
              val todo = Todo.create(
                ctx arg args.userId,
                ctx arg args.title,
                ctx arg args.description
              )
              ctx.ctx.todoDao.create(todo)
              todo
            }
          ),
          Field(
            "update",
            OptionType(todoType),
            arguments = args.id :: args.userId :: args.titleOpt :: args.descriptionOpt :: Nil,
            resolve = { ctx =>
              ctx.ctx.todoDao.findById(ctx arg args.id).collect {
                case todo if todo.userId == ctx.arg(args.userId) =>
                  val title = ctx.arg(args.titleOpt) getOrElse todo.title
                  val description = ctx.arg(args.descriptionOpt) getOrElse todo.description
                  val updatedTodo = todo.update(title, description)
                  ctx.ctx.todoDao.update(updatedTodo)
                  updatedTodo
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

  private object args {
    lazy val id = Argument("id", StringType, "id of user")
    lazy val email = Argument("email", StringType, "email of user")
    lazy val password = Argument("password", StringType, "password of user")
    lazy val name = Argument("name", StringType, "name of todo")
  }

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
        arguments = args.id :: Nil,
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.userDao.findById(c arg args.id)
        }
      ),
      Field(
        "by_email",
        OptionType(userType),
        arguments = args.email :: Nil,
        description = Some("find by email"),
        resolve = { c: Context[dao.container, Unit] =>
          c.ctx.userDao.findByEmail(c arg args.email)
        }
      )
    )
  )

  override lazy val mutation = {
    Some(
      ObjectType(
        "UserMutation",
        "UserMutation",
        fields[dao.container, Unit](
          Field(
            "create",
            userType,
            arguments = args.name :: args.email :: args.password :: Nil,
            resolve = { ctx =>
              val user = {
                val name = ctx arg args.name
                val email = ctx arg args.email
                val password = ctx arg args.password
                User.create(name, email, password)
              }
              ctx.ctx.userDao.create(user)
              user
            }
          ),
          Field(
            "login",
            OptionType(StringType),
            arguments = args.email :: args.password :: Nil,
            resolve = { ctx =>
              val (email, password) = (ctx arg args.email, ctx arg args.password)
              UpdateCtx(ctx.ctx.userDao.login(email, password)) { token: String =>
                val newCtx = ctx.ctx.loggedIn(token)
                log(s"logged in. email = $email, token = $token, newCtx = ${newCtx}")
                newCtx
              }
            }
          ),
          Field(
            "update",
            OptionType(userType),
            arguments = args.id :: args.name :: Nil,
            tags = SchemaDefinition.Authenticated :: Nil,
            resolve = { ctx =>
              println(s"update. ctx = ${ctx.ctx}")
              if (!ctx.ctx.isLoggedIn) throw AuthnException("you are not logged in.")
              else {
                ctx.ctx.userDao.findById(ctx arg args.id).map { user =>
                  user.updateName(ctx arg args.name) <| {
                    ctx.ctx.userDao.update
                  }
                }
              }
            }
          )
        )
      ))
  }

  lazy val fetcher: Fetcher[dao.container, User, User, Id] = Fetcher.caching {
    (ctx: dao.container, ids: Seq[Id]) =>
      Future.apply {
        ctx.userDao.findAllByIds(ids)
      }
  }

}
