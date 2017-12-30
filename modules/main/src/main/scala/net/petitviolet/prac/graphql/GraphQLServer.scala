package net.petitviolet.prac.graphql

import akka.http.scaladsl.model.StatusCodes
import net.petitviolet.prac.graphql.scheme.SchemaDefinition
import sangria.ast.Document
import sangria.execution.{ErrorWithResolver, Executor, QueryAnalysisError}
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.schema.Schema
import spray.json.{JsObject, JsString, JsValue}

import scala.concurrent.ExecutionContext

object GraphQLServer {
  def execute(jsValue: JsValue)(implicit ec: ExecutionContext) = {
    val JsObject(fields) = jsValue
    val JsString(query) = fields("query")

    val operation = fields.get("operationName") collect {
      case JsString(op) => op
    }

    val vars = fields.get("variables") match {
      case Some(obj: JsObject) => obj
      case _ => JsObject.empty
    }

    QueryParser.parse(query) map { queryDocument =>
      // query parsed successfully, time to execute it!
      executeGraphQLQuery(SchemaDefinition.query, queryDocument, dao.container, vars, operation)
    }
  }

  private def executeGraphQLQuery[Repository](schema: Schema[Repository, Unit],
                                              queryDocument: Document,
                                              repository: Repository,
                                              vars: JsObject,
                                              operation: Option[String])(implicit ec: ExecutionContext) = {
    import StatusCodes._
    Executor.execute(schema, queryDocument, repository,
      variables = vars,
      operationName = operation,
    ).map { OK -> _ }
      .recover {
        case error: QueryAnalysisError => BadRequest -> error.resolveError
        case error: ErrorWithResolver => InternalServerError -> error.resolveError
      }

  }

}
