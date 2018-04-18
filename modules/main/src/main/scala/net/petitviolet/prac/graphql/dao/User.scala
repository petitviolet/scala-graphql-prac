package net.petitviolet.prac.graphql.dao

import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.UUID

import spray.json.JsonFormat

import scala.util.{ Failure, Success, Try }

case class User(id: Id,
                name: String,
                email: String,
                hashedPassword: String,
                createdAt: ZonedDateTime,
                updatedAt: ZonedDateTime)
    extends Entity {
  def updateName(newName: String): User = copy(name = newName)
}

object User {
  def createToken = UUID.randomUUID().toString

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

case class AuthnException(msg: String) extends RuntimeException(s"failed to authn. [$msg]")

class UserDao extends RedisDao[User] {

  override protected val prefix: String = "user"
  override protected implicit val jsonFormat: JsonFormat[User] = User.userJsonFormat

  def authenticate(token: String): Option[User] = {
    withRedis { client =>
      withLogging(s"findByToken($token)") {
        client
          .get[Id](s"$prefix:token:$token")
          .flatMap {
            findById
          }
      }
    }
  }

  def login(email: String, password: String): Try[String] = {
    def storeToken(user: User, token: String) = {
      withRedis { client =>
        withLogging(s"storeToken. token -> $token") {
          client.set(s"$prefix:token:$token", user.id)
        }
      }
    }

    findByEmail(email)
      .collect {
        case user if User.login(user, password) =>
          val token = User.createToken
          storeToken(user, token)
          Success(token)
      }
      .getOrElse { Failure(AuthnException(s"email = $email")) }
  }

  override def create(entity: User): Unit = {
    def storeEmail(user: User): Unit = {
      withRedis { client =>
        withLogging(s"create: email -> ${user.email}") {
          client.set(s"$prefix:email:${user.email}", user.id)
        }
      }
    }

    super.create(entity)
    storeEmail(entity)
  }

  def findByEmail(email: String): Option[User] = {
    withRedis { client =>
      withLogging(s"findByEmail($email)") {
        client
          .get[Id](s"$prefix:email:$email")
          .flatMap {
            findById
          }
      }
    }
  }

  def findByToken(token: String): Option[User] = {
    withRedis { client =>
      withLogging(s"findByToken($token)") {
        client
          .get[Id](s"$prefix:token:$token")
          .flatMap { findById }
      }
    }
  }
}
