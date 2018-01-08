package net.petitviolet.prac.graphql

import java.util.concurrent.Executors

import akka.actor.{ ActorSystem, Terminated }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import spray.json._

import scala.concurrent.{ Await, ExecutionContext }
import scala.concurrent.duration._
import scala.io.StdIn

object main extends App {
  implicit val system: ActorSystem = ActorSystem("graphql-prac")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  private val executionContext =
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(sys.runtime.availableProcessors()))

  val route: Route =
    (post & path("graphql")) {
      entity(as[JsValue]) { jsObject =>
        complete(GraphQLServer.execute(jsObject)(executionContext))
      }
    } ~
      get {
        getFromResource("graphiql.html")
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
  println(s"shutdown completed!\n")

//  sys.exit(0)
}
