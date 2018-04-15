package net.petitviolet.prac.graphql.dao

abstract case class container private (userDao: UserDao,
                                       todoDao: TodoDao,
                                       tokenOpt: Option[String]) {

  def loggedIn(token: String): container = {
    container.apply(token)
  }

  def isLoggedIn: Boolean = tokenOpt.isDefined
}

object container {
  private lazy val userDao = new UserDao
  private lazy val todoDao = new TodoDao

  def apply(): container = new container(userDao, todoDao, None) {}
  def apply(token: String): container = new container(userDao, todoDao, Some(token)) {}
}
