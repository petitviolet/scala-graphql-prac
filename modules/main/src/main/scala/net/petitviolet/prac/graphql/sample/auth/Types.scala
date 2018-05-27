package net.petitviolet.prac.graphql.sample.auth

import scala.util.Try
import sangria.macros.derive
import sangria.schema._

case class User(id: Long, name: String, email: String, password: String) {
  def updateName(newName: String): User = copy(name = newName)
}

object UserDao {
  private val userMap: collection.mutable.Map[Token, User] = collection.mutable.HashMap(
    Token("token-1") -> User(1L, "user-1", "user-1@example.com", "password"),
    Token("token-2") -> User(2L, "user-2", "user-2@example.com", "password")
  )

  def findAll: Seq[User] = userMap.values.toList

  def login(email: String, password: String): Try[Token] =
    Try(userMap.collectFirst {
      case (token, user) if user.email == email && user.password == password => token
    }.get)

  def findByToken(token: Token): Option[User] = userMap.get(token)

  def update(newUser: User): Unit = userMap.update(Token(s"token-${newUser.id}"), newUser)
}
// for authentication
case class Token(value: String)

// use as `Ctx`
object Types {
  val userType: ObjectType[Unit, User] = derive.deriveObjectType[Unit, User]()
  val tokenType: ObjectType[Unit, Token] = derive.deriveObjectType[Unit, Token]()
}

private[auth] object args {
  lazy val name = Argument("name", StringType, "name of user")
  lazy val email = Argument("email", StringType, "email of user")
  lazy val password = Argument("password", StringType, "password of user")
  lazy val token = Argument("token", StringType, "token of logged in user")
}
