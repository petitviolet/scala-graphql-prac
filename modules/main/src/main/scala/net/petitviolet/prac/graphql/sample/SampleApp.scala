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
import sangria.ast.Document
import sangria.execution.{ ErrorWithResolver, QueryAnalysisError, _ }
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema
import spray.json.{ JsObject, JsString, JsValue }

import scala.concurrent._
import scala.concurrent.duration._
import scala.io.StdIn

trait GraphQLServerBase {
  type Ctx
  protected def schema: Schema[Ctx, Unit]
  protected def context: Ctx

  def showSchema: String = {
    schema.renderPretty
  }

  def execute(jsValue: JsValue)(implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
    val JsObject(fields) = jsValue
    val operation = fields.get("operationName") collect {
      case JsString(op) => op
    }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) => obj
      case _                   => JsObject.empty
    }

    val Some(JsString(document)) = fields.get("query")

    Future.fromTry(QueryParser.parse(document)) flatMap { queryDocument: Document =>
      Executor
        .execute(
          schema,
          queryDocument,
          context,
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

class SampleApp(server: GraphQLServerBase) {
  def main(args: Array[String]): Unit = {

    implicit val system: ActorSystem = ActorSystem("graphql-prac")
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    implicit val executionContext =
      ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

    val route: Route =
      (post & path("graphql")) {
        entity(as[JsValue]) { jsValue =>
          complete(server.execute(jsValue))
        }
      } ~
        (get & path("schema")) {
          complete(server.showSchema)
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
