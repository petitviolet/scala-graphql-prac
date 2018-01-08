package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class Users(id: Id,
                 name: String,
                 email: String,
                 createdAt: ZonedDateTime,
                 updatedAt: ZonedDateTime)
    extends Entity

class UsersDao {
  import collection.mutable
  private def now = ZonedDateTime.now()
  private val data: mutable.Map[Id, Users] = mutable.Map(
    "user-1" -> Users(
      "user-1",
      "hello-user-1",
      "hello-1@example.com",
      now,
      now,
    ),
    "user-2" -> Users(
      "user-2",
      "hello-user-2",
      "hello-2@example.com",
      now,
      now,
    ),
  )

  def findById(id: Id): Option[Users] = data.get(id)

  def findByEmail(emailOpt: Option[String]): Option[Users] = {
    emailOpt.flatMap { email =>
      data.values.find { _.email == email }
    }
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
