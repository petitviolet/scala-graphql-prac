package net.petitviolet.prac.graphql.scheme

import net.petitviolet.prac.graphql.dao
import net.petitviolet.prac.graphql.dao.{Todos, Users, UsersDao}
import net.petitviolet.prac.graphql.model.{Entity, Todo, User}
import sangria.execution.deferred.{Fetcher, HasId}
import sangria.macros.derive._
import sangria.schema._

import scala.concurrent.Future

object SchemaDefinition {
  val EntityInterface: InterfaceType[Unit, Entity] = InterfaceType(
    "Entity",
    fields[Unit, Entity](
      Field("id", StringType, resolve = _.value.id.value)
    )
  )

  val UserType: ObjectType[Unit, Users] = ObjectType(
    "User",
    fields[Unit, Users](
      Field("name", StringType, resolve = _.value.name),
      Field("email", StringType, resolve = _.value.email),
    )
  )
  val user = Fetcher.caching({ (ctx: UsersDao, ids:Seq[String]) =>
    Future.successful {
      ctx.findAllByIds(ids)
    }
  })(HasId(_.id))

//  val TodoType: ObjectType[Unit, Todos] = deriveObjectType[Unit, Todos](
//    ObjectTypeDescription("Todo")
//  )
}

object Query {
  val userQuery = Schema(UserQuery.QueryType)
}

object UserQuery {
  val Id = Argument("id", StringType)
  val QueryType = ObjectType(
    "UserQuery",
    fields[dao.UsersDao, Unit](
      Field("user", OptionType(SchemaDefinition.UserType),
        arguments = Id :: Nil,
        resolve = c => c.ctx.findById(c arg Id)
      )
    )
  )
}
