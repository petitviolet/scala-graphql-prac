package net.petitviolet.prac.graphql.sample

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import sangria.execution._
import sangria.macros.derive
import sangria.marshalling.sprayJson._
import sangria.marshalling._
import sangria.parser.QueryParser
import sangria.schema._
import spray.json.{ JsObject, JsString, JsValue }

import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn

object sample {
  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("graphql-prac")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

    val route: Route =
      (post & path("graphql")) {
        entity(as[JsValue]) { jsObject =>
//          logRequestResult("/graphql", Logging.InfoLevel) {
          complete(GraphQLServer.execute(jsObject)(executionContext))
//          }
        }
      } ~
        get {
          logRequestResult("/graphiql.html", Logging.InfoLevel) {
            getFromResource("graphiql.html")
          }
        }

    val host = sys.props.get("http.host") getOrElse "0.0.0.0"
    val port = sys.props.get("http.port").fold(8080)(_.toInt)

    val f = Http().bindAndHandle(route, host, port)

    println(s"server at [$host:$port]")

    val _ = StdIn.readLine("\ninput something\n")

    println("\nshutdown...\n")
    val x = f.flatMap { b =>
      b.unbind()
        .flatMap { _ =>
          materializer.shutdown()
          system.terminate()
        }(ExecutionContext.global)
    }(ExecutionContext.global)

    Await.ready(x, 5.seconds)
    sys.runtime.gc()
    println(s"shutdown completed!\n")
  }
}

private object GraphQLServer {
  private val repository = new SchemaSample.MyObjectRepository()

  def execute(jsValue: JsValue)(implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
    val JsObject(fields) = jsValue
    val operation = fields.get("operationName") collect {
      case JsString(op) => op
    }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) => obj
      case _                   => JsObject.empty
    }

    val Some(JsString(document)) = fields.get("query") orElse fields.get("mutation")

    Future.fromTry(QueryParser.parse(document)) flatMap { queryDocument =>
      Executor
        .execute(
          AuthSampleSchema.schema,
          queryDocument,
          AuthSampleSchema.GraphQLContext(),
          operationName = operation,
          variables = vars
        )
//        .execute(
//          SchemaSample.schema,
//          queryDocument,
//          repository,
//          operationName = operation,
//          variables = vars
//        )
        .map { jsValue =>
          OK -> jsValue
        }
        .recover {
          case error: QueryAnalysisError => BadRequest -> error.resolveError
          case error: ErrorWithResolver  => InternalServerError -> error.resolveError
        }
    }
  }
}

private object SchemaSample {
  case class MyObject(id: Long, name: String)

  class MyObjectRepository {

    import scala.collection.mutable

    private val data: mutable.Map[Long, MyObject] = mutable.LinkedHashMap(
      1L -> MyObject(1, "alice"),
      2L -> MyObject(2, "bob"),
    )

    def findAll: Seq[MyObject] = data.values.toList

    def findById(id: Long): Option[MyObject] = data get id

    def store(obj: MyObject): MyObject = {
      data += (obj.id -> obj)
      obj
    }

    def create(name: String): MyObject = {
      val id = data.keys.max + 1
      store(MyObject(id, name))
    }
  }

  val myObjectType: ObjectType[Unit, MyObject] = derive.deriveObjectType[Unit, MyObject]()

  lazy val myQuery: ObjectType[MyObjectRepository, Unit] = {
    ObjectType.apply(
      "MyQuery",
      fields[MyObjectRepository, Unit](
        {
          val idArg = Argument("id", LongType)
          Field(
            "find_by_id",
            OptionType(myObjectType),
            arguments = idArg :: Nil,
            resolve = ctx => ctx.ctx.findById(ctx.arg(idArg))
          )
        },
        Field("all", ListType(myObjectType), resolve = ctx => ctx.ctx.findAll),
      )
    )
  }

  val myObjectInputType: InputObjectType[MyObject] =
    InputObjectType[MyObject]("MyObjectInput",
                              List(
                                InputField("id", LongType),
                                InputField("name", StringType)
                              ))

  implicit val myObjectInput: FromInput[MyObject] = new FromInput[MyObject] {
    override val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): MyObject = {
      val m = node.asInstanceOf[Map[String, Any]]
      MyObject(m("id").asInstanceOf[Long], m("name").asInstanceOf[String])
    }
  }

  lazy val myMutation: ObjectType[MyObjectRepository, Unit] = {
    ObjectType.apply(
      "MyMutation",
      fields[MyObjectRepository, Unit](
        {
          val inputMyObject = Argument("my_object", myObjectInputType)
          Field(
            "store",
            arguments = inputMyObject :: Nil,
            fieldType = myObjectType,
            resolve = c => c.ctx.store(c arg inputMyObject)
          )
        }, {
          val inputName = Argument("name", StringType)
          Field(
            "create",
            arguments = inputName :: Nil,
            fieldType = myObjectType,
            resolve = c => c.ctx.create(c arg inputName)
          )
        }
      )
    )
  }

  lazy val schema: Schema[MyObjectRepository, Unit] = Schema(myQuery, Some(myMutation))
}

// just a toy sample
private object AuthSampleSchema {
  import scala.util.Try

  case class User(id: Long, name: String, email: String, password: String) {
    def updateName(newName: String): User = copy(name = newName)
  }
  private object UserDao {
    private val userMap: collection.mutable.Map[Token, User] = collection.mutable.HashMap(
      Token("token-1") -> User(1L, "user-1", "user-1@example.com", "password"),
      Token("token-2") -> User(2L, "user-2", "user-2@example.com", "password")
    )
    def findAll: Seq[User] = userMap.values.toList

    def login(email: String, password: String): Try[Token] =
      Try(userMap.collectFirst {
        case (token, user) if user.email == email && user.password == password => token
      }.get)

    def findByToken(token: Token): Option[User] = userMap.get(token)

    def update(newUser: User): Unit = userMap.update(Token(s"token-${newUser.id}"), newUser)
  }
  // for authentication
  case class Token(value: String)

  // use as `Ctx`
  case class GraphQLContext(userOpt: Option[User] = None) {
    private[AuthSampleSchema] val userDao = UserDao
    def loggedIn(user: User): GraphQLContext = copy(userOpt = Some(user))
    def authenticate(token: Token): GraphQLContext = copy(userOpt = userDao.findByToken(token))
  }
  private val userType: ObjectType[Unit, User] = derive.deriveObjectType[Unit, User]()
  private val tokenType: ObjectType[Unit, Token] = derive.deriveObjectType[Unit, Token]()

  private object args {
    lazy val name = Argument("name", StringType, "name of user")
    lazy val email = Argument("email", StringType, "email of user")
    lazy val password = Argument("password", StringType, "password of user")
    lazy val token = Argument("token", StringType, "token of logged in user")
  }

  private val authenticateFields = fields[GraphQLContext, Unit](
    Field(
      "authenticate",
      OptionType(userType),
      arguments = args.token :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.token) { (token) =>
          // execute authentication process
          UpdateCtx(ctx.ctx.userDao.findByToken(Token(token))) { userOpt =>
            userOpt.fold(ctx.ctx) { user =>
              // when found user, update ctx
              val newCtx = ctx.ctx.loggedIn(user)
              println(s"newCtx: ${newCtx}")
              newCtx
            }
          }
        }
      }
    ),
    Field(
      "login",
      OptionType(tokenType),
      arguments = args.email :: args.password :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.email, args.password) { (email, password) =>
          // execute login process
          UpdateCtx(ctx.ctx.userDao.login(email, password)) { token: Token =>
            // when succeeded, update ctx
            val loggedInUser = ctx.ctx.userDao.findByToken(token).get
            val newCtx = ctx.ctx.loggedIn(loggedInUser)
            println(s"newCtx: ${newCtx}")
            newCtx
          }
        }
      }
    )
  )

  private val loggedInUserField: Field[GraphQLContext, Unit] = Field(
    "get",
    OptionType(userType),
    arguments = Nil,
    resolve = ctx => ctx.ctx.userOpt
  )

  private val userQuery = fields[GraphQLContext, Unit](loggedInUserField)

  private val userMutation = fields[GraphQLContext, Unit](
    loggedInUserField,
    Field(
      "update",
      userType,
      arguments = args.name :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.name) { name =>
          // if not authenticated, its ridiculous!
          val user = ctx.ctx.userOpt.get
          val newUser = user.updateName(name)
          UserDao.update(newUser)
          newUser
        }
      }
    )
  )

  private val query =
    ObjectType("Query", fields[GraphQLContext, Unit](authenticateFields ++ userQuery: _*))
  private val mutation =
    ObjectType("Mutation", fields[GraphQLContext, Unit](authenticateFields ++ userMutation: _*))

  lazy val schema: Schema[GraphQLContext, Unit] = Schema(query, Some(mutation))
}
