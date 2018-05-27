package net.petitviolet.prac.graphql.sample.auth

import net.petitviolet.prac.graphql.sample.auth.Types._
import sangria.schema._

// just a toy sample
object AuthWithUpdateCtx {

  private val authenticateFields = fields[GraphQLContext, Unit](
    Field(
      "authenticate",
      OptionType(userType),
      arguments = args.token :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.token) { (token) =>
          // execute authentication process
          UpdateCtx(ctx.ctx.userDao.findByToken(Token(token))) { userOpt =>
            userOpt.fold(ctx.ctx) { user =>
              // when found user, update ctx
              val newCtx = ctx.ctx.loggedIn(user)
              println(s"newCtx: ${newCtx}")
              newCtx
            }
          }
        }
      }
    ),
    Field(
      "login",
      OptionType(tokenType),
      arguments = args.email :: args.password :: Nil,
      resolve = { ctx =>
        ctx.withArgs(args.email, args.password) { (email, password) =>
          // execute login process
          UpdateCtx(ctx.ctx.userDao.login(email, password)) { token: Token =>
            // when succeeded, update ctx
            val loggedInUser = ctx.ctx.userDao.findByToken(token).get
            val newCtx = ctx.ctx.loggedIn(loggedInUser)
            println(s"newCtx: ${newCtx}")
            newCtx
          }
        }
      }
    )
  )

  private val loggedInUserField: Field[GraphQLContext, Unit] = Field(
    "get",
    OptionType(userType),
    arguments = Nil,
    resolve = ctx => ctx.ctx.userOpt
  )

  private val userQuery = fields[GraphQLContext, Unit](loggedInUserField)

  private val userMutation = fields[GraphQLContext, Unit](
    loggedInUserField,
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
    ObjectType(
      "Query",
      fields[GraphQLContext, Unit](
        Field(
          "User",
          ObjectType("UserType", fields[GraphQLContext, Unit](authenticateFields ++ userQuery: _*)),
          resolve = _ => ()
        )
      ))

  private val mutation =
    ObjectType("Mutation",
               authenticateFields ++ fields[GraphQLContext, Unit](
                 Field("UserY",
                       ObjectType("UserZ", fields[GraphQLContext, Unit](userMutation: _*)),
                       resolve = _ => ())
               ))
//  private val mutation =
//    ObjectType(
//      "Mutation",
//      fields[GraphQLContext, Unit](
//        Field("prefix",
//              ObjectType("prefix",
//                         fields[GraphQLContext, Unit](authenticateFields ++ userMutation: _*)),
//              resolve = _ => ())
//      ))

  lazy val schema: Schema[GraphQLContext, Unit] = Schema(query, Some(mutation))
}
