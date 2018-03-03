package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

case class User(id: Id,
                name: String,
                email: String,
                createdAt: ZonedDateTime,
                updatedAt: ZonedDateTime)
    extends Entity

object User {
  def create(name: String, email: String): User = {
    require(name.nonEmpty && email.nonEmpty, "name and email must not empty.")

    val dateTime = now()
    apply(
      generateId,
      name,
      email,
      dateTime,
      dateTime
    )
  }
}

class UserDao {
  import collection.mutable
  private def now = ZonedDateTime.now()
  private val data: mutable.Map[Id, User] = {
    def _user(id: Id): (Id, User) = id -> User(id, s"${id}_name", s"$id@example.com", now, now)
    mutable.Map(
      _user("user-1"),
      _user("user-2"),
      _user("user-3")
    )
  }

  def findById(id: Id): Option[User] = {
    println(s"UserDao#findById($id)")
    data.get(id)
  }

  def findByEmail(email: String): Option[User] = {
    data.values.find { _.email == email }
  }

  def findAllByIds(ids: Seq[Id]): Seq[User] = {
    println(s"UserDao#findAllById($ids)")
    ids collect data
  }

  def findAll: Seq[User] = data.values.toList

  def create(users: User): Unit = data += (users.id -> users)

  def update(users: User): Unit =
    data.update(
      users.id,
      users
    )
}
