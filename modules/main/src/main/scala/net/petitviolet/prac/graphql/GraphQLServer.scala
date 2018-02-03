package net.petitviolet.prac.graphql

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import net.petitviolet.prac.graphql.dao.container
import net.petitviolet.prac.graphql.scheme.SchemaDefinition
import sangria.ast.Document
import sangria.execution.deferred.DeferredResolver
import sangria.execution._
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.Schema
import spray.json.{ JsObject, JsString, JsValue }

import scala.concurrent.{ ExecutionContext, Future }

object GraphQLServer {
  private def schema = SchemaDefinition.schema

  def showSchema: String = SchemaRenderer.renderSchema(schema)

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
      // query parsed successfully, time to execute it!
      executeGraphQL(
        schema,
        queryDocument,
        vars,
        operation,
        dao.container,
        SchemaDefinition.resolver,
        Middlewares.logging :: Nil,
      )
    }
  }

  private def executeGraphQL[Repository](
      schema: Schema[Repository, Unit],
      document: Document,
      vars: JsObject,
      operation: Option[String],
      repository: Repository,
      deferredResolver: DeferredResolver[Repository],
      middlewares: List[Middleware[Repository]] = Nil,
  )(implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
    import StatusCodes._
    Executor
      .execute(
        schema,
        document,
        repository,
        variables = vars,
        operationName = operation,
        deferredResolver = deferredResolver,
        middleware = middlewares
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

object Middlewares {
  private val logger = new {
    def info(s: => String): Unit = println(s"[logging]$s")
  }

  val logging: Middleware[dao.container] = new Middleware[dao.container] {
    override type QueryVal = Unit

    override def beforeQuery(context: MiddlewareQueryContext[container, _, _]): QueryVal = {
      logger.info(s"before OperationName: ${context.operationName}")
    }

    override def afterQuery(queryVal: QueryVal,
                            context: MiddlewareQueryContext[container, _, _]): Unit = {
      logger.info(s"after queryVal: $queryVal")
    }
  }
}
