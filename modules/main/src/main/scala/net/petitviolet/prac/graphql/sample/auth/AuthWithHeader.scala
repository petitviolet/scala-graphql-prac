package net.petitviolet.prac.graphql.sample.auth

import net.petitviolet.prac.graphql.sample.auth.Types._
import sangria.schema._

// just a toy sample
object AuthWithHeader {
  case class GraphQLContext private (userOpt: Option[User] = None) {
    private[auth] val userDao = UserDao
  }

  object GraphQLContext {
    def create(tokenOpt: Option[String]): GraphQLContext = {
      tokenOpt.fold(apply()) { token =>
        apply(UserDao.findByToken(Token(token)))
      }
    }
  }

  private val loginField = fields[GraphQLContext, Unit](
    Field(
      "login",
      OptionType(tokenType),
      arguments = args.email :: args.password :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.email, args.password) { (email, password) =>
          // execute login process
          ctx.ctx.userDao.login(email, password)
        }
      }
    )
  )

  private val viewerField: Field[GraphQLContext, Unit] = Field(
    "viewer",
    OptionType(userType),
    arguments = Nil,
    resolve = ctx => ctx.ctx.userOpt
  )

  private val userMutation = fields[GraphQLContext, Unit](
    Field(
      "update",
      userType,
      arguments = args.name :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.name) { name =>
          // if not authenticated, its ridiculous!
          val user = ctx.ctx.userOpt.get
          val newUser = user.updateName(name)
          UserDao.update(newUser)
          newUser
        }
      }
    )
  )

  private val query =
    ObjectType("Query", viewerField +: loginField)

  private val mutation =
    ObjectType("Mutation",
               loginField ++ fields[GraphQLContext, Unit](
                 Field("UserY",
                       ObjectType("UserZ", fields[GraphQLContext, Unit](userMutation: _*)),
                       resolve = _ => ())
               ))

  lazy val schema: Schema[GraphQLContext, Unit] = Schema(query, Some(mutation))
}
