package net.petitviolet.prac.graphql.dao

trait container {
  def usersDao: UsersDao
  def todoDao: TodosDao
}

object container extends container {
  override lazy val usersDao = new UsersDao
  override lazy val todoDao = new TodosDao
}
