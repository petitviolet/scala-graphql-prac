package net.petitviolet.prac.graphql.dao

import java.security.MessageDigest
import java.time.ZonedDateTime

import spray.json.JsonFormat

import scala.util.{ Failure, Success, Try }

case class User(id: Id,
                name: String,
                email: String,
                hashedPassword: String,
                createdAt: ZonedDateTime,
                updatedAt: ZonedDateTime)
    extends Entity

object User {
  private val digest = MessageDigest.getInstance("SHA-256")
  private def hash(id: Id, str: String): String = {
    digest
      .digest((id + str).getBytes)
      .map { b =>
        "%02x".format(b)
      }
      .mkString
  }

  def login(user: User, password: String): Boolean = {
    hash(user.id, password) == user.hashedPassword
  }

  def create(name: String, email: String, password: String): User = {
    val id = generateId
    require(name.nonEmpty && email.nonEmpty, "name and email must not empty.")

    val dateTime = now()
    apply(
      id,
      name,
      email,
      hash(id, password),
      dateTime,
      dateTime
    )
  }
  import spray.json._

  val userJsonFormat: RootJsonFormat[User] = DefaultJsonProtocol.jsonFormat6(User.apply)
}

case class AuthorizationException(email: String)
    extends RuntimeException(s"failed to login. email = $email")

class UserDao extends RedisDao[User] {

  override protected val prefix: String = "user"
  override protected implicit val jsonFormat: JsonFormat[User] = User.userJsonFormat

  def login(email: String, password: String): Try[User] = {
    findByEmail(email)
      .collect { case user if User.login(user, password) => Success(user) }
      .getOrElse { Failure(AuthorizationException(email)) }
  }

  def findByEmail(email: String): Option[User] = {
    findAll.find { _.email == email }
  }

}
