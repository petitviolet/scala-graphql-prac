package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class User(id: Id,
                name: String,
                email: String,
                createdAt: ZonedDateTime,
                updatedAt: ZonedDateTime)
    extends Entity

class UserDao {
  import collection.mutable
  private def now = ZonedDateTime.now()
  private val data: mutable.Map[Id, User] = mutable.Map(
    "user-1" -> User(
      "user-1",
      "hello-user-1",
      "hello-1@example.com",
      now,
      now,
    ),
    "user-2" -> User(
      "user-2",
      "hello-user-2",
      "hello-2@example.com",
      now,
      now,
    ),
  )

  def findById(id: Id): Option[User] = data.get(id)

  def findByEmail(emailOpt: Option[String]): Option[User] = {
    emailOpt.flatMap { email =>
      data.values.find { _.email == email }
    }
  }

  def findAllByIds(ids: Seq[Id]): Seq[User] = ids collect data

  def findAll: Seq[User] = data.values.toList

  def create(users: User): Unit = data += (users.id -> users)

  def update(users: User): Unit =
    data.update(
      users.id,
      users
    )
}
