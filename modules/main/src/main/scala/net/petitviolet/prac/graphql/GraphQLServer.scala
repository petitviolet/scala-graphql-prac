package net.petitviolet.prac.graphql

import akka.http.scaladsl.model.{StatusCode, StatusCodes}
import net.petitviolet.prac.graphql.scheme.SchemaDefinition
import sangria.ast.Document
import sangria.execution.deferred.DeferredResolver
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.schema.Schema
import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.{ExecutionContext, Future}

object GraphQLServer {
  def execute(jsValue: JsValue)(implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
    val JsObject(fields) = jsValue
    val operation = fields.get("operationName") collect {
      case JsString(op) => op
    }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) => obj
      case _ => JsObject.empty
    }

    val Some(JsString(document)) = fields.get("query") orElse fields.get("mutation")

    Future.fromTry(QueryParser.parse(document)) flatMap { queryDocument =>
      // query parsed successfully, time to execute it!
      executeGraphQL(SchemaDefinition.schema, queryDocument, vars, operation,
        dao.container, SchemaDefinition.resolver)
    }
  }

  private def executeGraphQL[Repository](schema: Schema[Repository, Unit],
                                         document: Document,
                                         vars: JsObject,
                                         operation: Option[String],
                                         repository: Repository,
                                         deferredResolver: DeferredResolver[Repository],
                                        )(implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
    import StatusCodes._
    Executor.execute(schema, document, repository,
      variables = vars,
      operationName = operation,
      deferredResolver = deferredResolver,
    ).map { jsValue => OK -> jsValue }
      .recover {
        case error: QueryAnalysisError => BadRequest -> error.resolveError
        case error: ErrorWithResolver => InternalServerError -> error.resolveError
      }

  }

}
