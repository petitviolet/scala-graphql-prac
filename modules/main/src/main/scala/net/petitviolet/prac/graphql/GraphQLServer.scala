package net.petitviolet.prac.graphql

import akka.http.scaladsl.model.{ StatusCode, StatusCodes }
import net.petitviolet.prac.graphql.dao.AuthnException
import net.petitviolet.prac.graphql.scheme.{ Authentication, SchemaDefinition }
import org.slf4j.LoggerFactory
import sangria.ast.Document
import sangria.execution.deferred.DeferredResolver
import sangria.execution._
import sangria.marshalling.sprayJson._
import sangria.parser.QueryParser
import sangria.renderer.SchemaRenderer
import sangria.schema.{ Context, Schema }
import spray.json.{ JsObject, JsString, JsValue }

import scala.concurrent.{ ExecutionContext, Future }

object GraphQLServer {
  private def schema = SchemaDefinition.schema

  def showSchema: String = SchemaRenderer.renderSchema(schema)

  def execute(jsValue: JsValue, tokenOpt: Option[String])(
      implicit ec: ExecutionContext): Future[(StatusCode, JsValue)] = {
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
        GraphQLContext(tokenOpt),
        SchemaDefinition.resolver,
        Middlewares.values
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
        middleware = middlewares,
        exceptionHandler = Authentication.errorHandler
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
  protected val logger = LoggerFactory.getLogger(this.getClass)

  lazy val values: List[Middleware[GraphQLContext]] = auth :: logging :: Nil

  private val auth = new Middleware[GraphQLContext] with MiddlewareBeforeField[GraphQLContext] {
    override type QueryVal = Unit
    override type FieldVal = Unit

    override def beforeQuery(context: MiddlewareQueryContext[GraphQLContext, _, _]): Unit = ()

    override def afterQuery(queryVal: QueryVal,
                            context: MiddlewareQueryContext[GraphQLContext, _, _]): Unit = ()

    override def beforeField(
        queryVal: QueryVal,
        mctx: MiddlewareQueryContext[GraphQLContext, _, _],
        ctx: Context[GraphQLContext, _]): BeforeFieldResult[GraphQLContext, Unit] = {
      val requireAuth = ctx.field.tags contains Authentication.Authenticated

      logger.info(s"[auth]field: ${ctx.field.name}, requireAuth: $requireAuth, ctx: ${ctx.ctx}")

      if (!requireAuth || (requireAuth && ctx.ctx.isLoggedIn)) {
        continue
      } else {
        val error = AuthnException(s"you must login!. field: ${ctx.field.name}")
        logger.error("error!", error)
        throw error
      }
    }
  }

  private val logging: Middleware[GraphQLContext] = new Middleware[GraphQLContext] {
    override type QueryVal = Unit

    override def beforeQuery(context: MiddlewareQueryContext[GraphQLContext, _, _]): QueryVal = {
      logger.info(s"before OperationName: ${context.operationName}")
    }

    override def afterQuery(queryVal: QueryVal,
                            context: MiddlewareQueryContext[GraphQLContext, _, _]): Unit = {
      logger.info(s"after queryVal: $queryVal")
    }
  }
}
