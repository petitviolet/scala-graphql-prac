package net.petitviolet.prac.graphql

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server._
import akka.http.scaladsl.{ Http, server }
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ Await, ExecutionContext }
import scala.io.StdIn

object main extends App with Directives {
  implicit val system: ActorSystem = ActorSystem("graphql-prac")
  implicit val materializer: ActorMaterializer = ActorMaterializer()
  private val config = ConfigFactory.load()
  private val myLogger = LoggerFactory.getLogger(this.getClass)
  private val awesomeLogger = LoggerFactory.getLogger("awesome_log")

  private val executionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

  def withLogging: server.Directive0 = {
    extractRequestContext.flatMap { ctx ⇒
      myLogger.info(s"[request]uri: ${ctx.request.uri}, header: ${ctx.request.headers}")
      awesomeLogger.info(s"[request]uri: ${ctx.request.uri}, header: ${ctx.request.headers}")
      mapRouteResult { result ⇒
        myLogger.info(s"[response]${result}")
        awesomeLogger.info(s"[response]${result}")
        result
      }
    }
  }

  val route: Route =
    (get & pathPrefix("index") & extractUri & headerValueByName("User-Agent")) { (uri, ua) =>
      withLogging {
        complete(s"param: ${uri.query().toMap}, user-agent: ${ua}}")
      }
    } ~
      (get & path("config") & withLogging) {
        complete(s"env: ${config.getString("my.configuration.env")}")
      } ~
      (post & path("graphql")) {
        entity(as[JsValue]) { jsObject =>
          logRequestResult("/graphql", Logging.InfoLevel) {
            complete(GraphQLServer.execute(jsObject)(executionContext))
          }
        }
      } ~
      (get & path("show")) {
        complete(GraphQLServer.showSchema)
      } ~
      get {
        logRequestResult("/graphiql.html", Logging.InfoLevel) {
          getFromResource("graphiql.html")
        }
      }

  val host = sys.props.get("http.host") getOrElse "0.0.0.0"
  val port = sys.props.get("http.port").fold(8080) { _.toInt }

  val f = Http().bindAndHandle(route, host, port)

  myLogger.info(s"server at [$host:$port]")

  Await.ready(f, Duration.Inf)
  sys.addShutdownHook {
    myLogger.info("\nshutdown...\n")
    val x = f.flatMap { b =>
      b.unbind()
        .flatMap { _ =>
          materializer.shutdown()
          system.terminate()
        }(ExecutionContext.global)
    }(ExecutionContext.global)

    Await.ready(x, Duration.Inf)
    myLogger.info(s"shutdown completed!\n")
  }
  val _ = StdIn.readLine("\ninput something\n")
}
