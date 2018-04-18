package net.petitviolet.prac.graphql.scheme

import java.util.concurrent.Executors

import net.petitviolet.operator.toPipe
import net.petitviolet.prac.graphql.GraphQLContext
import net.petitviolet.prac.graphql.dao.{ AuthnException, Id, Todo, Token, User }
import org.slf4j.LoggerFactory
import sangria.execution.{ ExceptionHandler, FieldTag, HandledException }
import sangria.execution.deferred.{ DeferredResolver, Fetcher, Relation }
import sangria.macros.derive
import sangria.schema._

import scala.concurrent.{ ExecutionContext, Future }

object SchemaDefinition {
  case object Authenticated extends FieldTag
  val errorHandler = ExceptionHandler {
    case (_, AuthnException(msg)) =>
      HandledException(msg)
  }

  private lazy val schemas: Seq[MySchema] = List(
    UserSchema,
    TodoSchema,
  )

  lazy val queryType: ObjectType[GraphQLContext, Unit] = ObjectType.apply(
    "Query",
    fields[GraphQLContext, Unit](
      schemas.map { _.asQueryField }: _*
    )
  )
  lazy val mutationType: Option[ObjectType[GraphQLContext, Unit]] = {
    val mutationFields = schemas.flatMap { _.asMutationField }
    if (mutationFields.isEmpty) None
    else
      Option.apply {
        ObjectType.apply(
          "Mutation",
          fields[GraphQLContext, Unit](
            mutationFields: _*
          )
        )
      }
  }
  lazy val resolver: DeferredResolver[GraphQLContext] = DeferredResolver.fetchers(
    schemas.map { _.fetcher }: _*
  )

  lazy val schema = Schema.apply(queryType, mutationType)
}

sealed trait MySchema {
  private val logger = LoggerFactory.getLogger(this.getClass)

  protected def log(msg: => String): Unit = logger.info(s"[$name]$msg")

  def name: String

  def query: ObjectType[GraphQLContext, Unit]

  def mutation: Option[ObjectType[GraphQLContext, Unit]] = None

  def asQueryField: Field[GraphQLContext, Unit] = Field(name, query, resolve = _ => ())

  def asMutationField: Option[Field[GraphQLContext, Unit]] = mutation.map { m =>
    Field(name, m, resolve = _ => ())
  }

  def fetcher: Fetcher[GraphQLContext, _, _, _]

  protected val threadNum: Int = 2
  protected implicit val ec: ExecutionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadNum))
}

object TodoSchema extends MySchema {

  override def name = "todo"

  val _: Fetcher[GraphQLContext, Todo, User, String] = {
    val byUser = Relation.apply[Todo, String]("byUser", { todo: Todo =>
      todo.userId :: Nil
    })

    Fetcher.rel[GraphQLContext, Todo, User, String](
      { (repo, ids) =>
        Future.apply(repo.todoDao.findAllByIds(ids))
      }, { (repo, ids) =>
        Future.apply {
          repo.userDao.findAllByIds(ids apply byUser)
        }
      }
    )
  }

  lazy val todoType: ObjectType[GraphQLContext, Todo] = derive.deriveObjectType(
    derive.Interfaces[GraphQLContext, Todo](entityType[GraphQLContext]),
    derive.AddFields(
      Field("user", OptionType(UserSchema.userType), resolve = {
        ctx: Context[GraphQLContext, Todo] =>
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
    fields[GraphQLContext, Unit](
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
        fields[GraphQLContext, Unit](
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

  lazy val fetcher: Fetcher[GraphQLContext, Todo, Todo, String] = Fetcher.caching {
    (ctx: GraphQLContext, ids: Seq[String]) =>
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
  lazy val authenticateType: ObjectType[Unit, Token] = derive.deriveObjectType[Unit, Token]()

  private object args {
    lazy val id = Argument("id", StringType, "id of user")
    lazy val email = Argument("email", StringType, "email of user")
    lazy val password = Argument("password", StringType, "password of user")
    lazy val name = Argument("name", StringType, "name of todo")
  }

  lazy val query: ObjectType[GraphQLContext, Unit] = ObjectType(
    "UserQuery",
    "user query(all/by_id/by_email)",
    fields[GraphQLContext, Unit](
      Field(
        "all",
        ListType(userType),
        description = Some("list all users"),
        arguments = Nil,
        resolve = { c: Context[GraphQLContext, Unit] =>
          c.ctx.userDao.findAll
        }
      ),
      Field(
        "by_id",
        OptionType(userType),
        description = Some("find by id"),
        arguments = args.id :: Nil,
        resolve = { c: Context[GraphQLContext, Unit] =>
          c.ctx.userDao.findById(c arg args.id)
        }
      ),
      Field(
        "by_email",
        OptionType(userType),
        arguments = args.email :: Nil,
        description = Some("find by email"),
        resolve = { c: Context[GraphQLContext, Unit] =>
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
        fields[GraphQLContext, Unit](
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
            authenticateType,
            arguments = args.email :: args.password :: Nil,
            resolve = { ctx =>
              val (email, password) = (ctx arg args.email, ctx arg args.password)

              UpdateCtx(ctx.ctx.userDao.login(email, password)) { token =>
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

  lazy val fetcher: Fetcher[GraphQLContext, User, User, Id] = Fetcher.caching {
    (ctx: GraphQLContext, ids: Seq[Id]) =>
      Future.apply {
        ctx.userDao.findAllByIds(ids)
      }
  }

}
