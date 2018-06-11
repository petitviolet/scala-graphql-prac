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
import sangria.parser.QueryParser
import sangria.schema._
import spray.json.{ JsObject, JsString, JsValue }

import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn


/**
 * {{{
 *
 * query MyQuery {
 *   all {
 *   __typename
 *   id
 *   name
 *   ...on Dog {
 *     kind
 *   }
 *   ...on Cat {
 *     color
 *   }
 * }
}

 * }}}
 */
object UnionInterfaceSampleApp {
  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("graphql-prac")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

    val route: Route =
      (post & path("graphql")) {
        entity(as[JsValue]) { jsObject =>
//          logRequestResult("/graphql", Logging.InfoLevel) {
          complete(AnimalGraphQLServer.execute(jsObject)(executionContext))
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

private object AnimalGraphQLServer {
  private val repository = new UnionInterfaceSampleSchema.AnimalRepository()

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
          UnionInterfaceSampleSchema.schema,
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

private object UnionInterfaceSampleSchema {
  trait Animal {
    def id: String
    def name: String
  }
  case class Dog(id: String, name: String, kind: String) extends Animal
  case class Cat(id: String, name: String, color: Color) extends Animal

  sealed abstract class Color(val rgb: String)
  object Color {
    case object White extends Color("#FFFFFF")
    case object Black extends Color("#000000")
    case object Brown extends Color("#A52A2A")
  }

  class AnimalRepository {
    import scala.collection.mutable
    private val data: mutable.Seq[Animal] = mutable.ListBuffer(
      Dog("dog-1", "alice", "golden"),
      Dog("dog-2", "bob", "Chihuahua"),
      Cat("cat-1", "charlie", Color.Brown)
    )

    def findAll: Seq[Animal] = data.toList

    def findById(id: String): Option[Animal] = data.find { a =>
      a.id == id
    }
  }

  lazy val animalInterface: InterfaceType[Unit, Animal] = InterfaceType[Unit, Animal](
    "Animal",
    "animal interface",
    fields[Unit, Animal](
      Field("id", StringType, resolve = ctx => ctx.value.id),
      Field("name", StringType, resolve = ctx => ctx.value.name)
    )
  )

  lazy val animalUnionType = UnionType[Unit]("AnimalUnion", types = dogType :: catType :: Nil)

  lazy val dogType = derive.deriveObjectType[Unit, Dog](
    derive.Interfaces[Unit, Dog](animalInterface)
  )

  implicit lazy val colorEnum = derive.deriveEnumType[Color]()

  lazy val catType = derive.deriveObjectType[Unit, Cat](
    derive.Interfaces[Unit, Cat](animalInterface)
  )

  private object args {}

  lazy val animalQuery: ObjectType[AnimalRepository, Unit] = {
    ObjectType.apply(
      "AnimalQuery",
      fields[AnimalRepository, Unit](
        Field("all", ListType(animalInterface), resolve = ctx => ctx.ctx.findAll)
      )
    )
  }
  lazy val schema: Schema[AnimalRepository, Unit] =
    Schema(animalQuery, additionalTypes = animalUnionType.types)
}
