package net.petitviolet.prac.graphql

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.ActorMaterializer
import spray.json._

import scala.io.StdIn

object main extends App {
  implicit val system: ActorSystem = ActorSystem("graphql-prac")
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  import system.dispatcher

  val route: Route =
    (post & path("graphql")) {
      entity(as[JsValue]) { jsObject =>
        complete(GraphQLServer.execute(jsObject))
      }
    } ~
      get {
        getFromResource("graphiql.html")
      }

  val f = Http().bindAndHandle(route, "0.0.0.0", sys.props.get("http.port").fold(8080)(_.toInt))

  val _ = StdIn.readLine("input something\n")

  println("\nshutdown...\n")

  f.flatMap { b =>
    b.unbind()
  }
  system.terminate()
}
