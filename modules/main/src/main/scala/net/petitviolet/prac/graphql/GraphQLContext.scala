package net.petitviolet.prac.graphql

import net.petitviolet.prac.graphql.dao.{ TodoDao, Token, User, UserDao }

abstract case class GraphQLContext private (userDao: UserDao,
                                            todoDao: TodoDao,
                                            tokenOpt: Option[Token] = None,
                                            userOpt: Option[User] = None) {

  def loggedIn(token: Token): GraphQLContext = {
    GraphQLContext.apply(token)
  }

  def authenticate(token: Token): GraphQLContext = {
    GraphQLContext.apply(token)
  }

  def isLoggedIn: Boolean = userOpt.isDefined
}

object GraphQLContext {
  private lazy val userDao = new UserDao
  private lazy val todoDao = new TodoDao

  def apply(): GraphQLContext = new GraphQLContext(userDao, todoDao, None, None) {}

  def apply(tokenOpt: Option[String]): GraphQLContext = {
    tokenOpt.fold(apply()) { Token.apply _ andThen apply }
  }
  def apply(token: Token): GraphQLContext = {
    val user = userDao.authenticate(token)
    new GraphQLContext(userDao, todoDao, Some(token), user) {}
  }

  def apply(token: Token, user: User): GraphQLContext =
    new GraphQLContext(userDao, todoDao, Some(token), Some(user)) {}
}
