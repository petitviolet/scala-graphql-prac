package net.petitviolet.prac.graphql

import net.petitviolet.prac.graphql.dao.{ TodoDao, User, UserDao }

abstract case class GraphQLContext private (userDao: UserDao,
                                            todoDao: TodoDao,
                                            tokenOpt: Option[String] = None,
                                            userOpt: Option[User] = None) {

  def loggedIn(token: String): GraphQLContext = {
    GraphQLContext.apply(token)
  }

  def isLoggedIn: Boolean = userOpt.isDefined
}

object GraphQLContext {
  private lazy val userDao = new UserDao
  private lazy val todoDao = new TodoDao

  def apply(): GraphQLContext = new GraphQLContext(userDao, todoDao, None, None) {}

  def apply(tokenOpt: Option[String]): GraphQLContext = {
    tokenOpt.fold(apply()) { apply }
  }
  def apply(token: String): GraphQLContext = {
    val user = userDao.findByToken(token)
    new GraphQLContext(userDao, todoDao, Some(token), user) {}
  }

  def apply(user: User): GraphQLContext = new GraphQLContext(userDao, todoDao, None, Some(user)) {}
}
