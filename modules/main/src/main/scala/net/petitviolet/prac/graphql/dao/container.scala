package net.petitviolet.prac.graphql.dao

sealed trait container {
  def userDao: UserDao
  def todoDao: TodoDao
}

object container extends container {
  override lazy val userDao = new UserDao
  override lazy val todoDao = new TodoDao
}
