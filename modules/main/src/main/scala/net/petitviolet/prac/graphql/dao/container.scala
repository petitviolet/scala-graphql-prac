package net.petitviolet.prac.graphql.dao

trait container {
  def usersDao: UsersDao
  def todosDao: TodosDao
}

object container extends container {
  override lazy val usersDao = new UsersDao
  override lazy val todosDao = new TodosDao
}
