package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Users(id: Id, name: String, email: String, createdAt: ZonedDateTime, updatedAt: ZonedDateTime)

class UsersDao {
  import collection.mutable
  private val data: mutable.Map[Id, Users] = mutable.Map(
    "hello" -> Users("1", "hello-user", "hello@example.com", ZonedDateTime.now(), ZonedDateTime.now())
  )

  def findById(id: Id): Option[Users] = data.get(id)
  def findAllByIds(ids: Seq[Id]): Seq[Users] = ids collect data

  def create(users: Users): Unit = data += (users.id -> users)

  def update(users: Users): Unit = data.update(users.id, users)
}
