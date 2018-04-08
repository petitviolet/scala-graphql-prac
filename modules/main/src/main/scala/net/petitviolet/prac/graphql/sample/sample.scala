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
  def hoge(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("graphql-prac")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

    val route: Route =
      (post & path("graphql")) {
        entity(as[JsValue]) { jsObject =>
          logRequestResult("/graphql", Logging.InfoLevel) {
            complete(GraphQLServer.execute(jsObject)(executionContext))
          }
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
          SchemaSample.schema,
          queryDocument,
          repository,
          operationName = operation,
          variables = vars
        )
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
