package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Users(id: Id,
                 name: String,
                 email: String,
                 createdAt: ZonedDateTime,
                 updatedAt: ZonedDateTime)

class UsersDao {
  import collection.mutable
  private val data: mutable.Map[Id, Users] = mutable.Map(
    "hello" -> Users(
      "hello",
      "hello-user",
      "hello@example.com",
      ZonedDateTime.now(),
      ZonedDateTime.now()
    )
  )

  def findById(id: Id): Option[Users] = data.get(id)

  def findByEmail(emailOpt: Option[String]): Option[Users] = {
    emailOpt.map { email =>
      data.values.find { _.email == email }
    } getOrElse None
  }

  def findAllByIds(ids: Seq[Id]): Seq[Users] = ids collect data

  def findAll: Seq[Users] = data.values.toList

  def create(users: Users): Unit = data += (users.id -> users)

  def update(users: Users): Unit =
    data.update(
      users.id,
      users
    )
}
