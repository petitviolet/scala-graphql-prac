package net.petitviolet.prac.graphql.dao

import java.time.ZonedDateTime

import com.redis.serialization.Parse
import spray.json.JsonFormat

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
  import spray.json._

  val userJsonFormat: RootJsonFormat[User] = DefaultJsonProtocol.jsonFormat5(User.apply)
}

class UserDao extends RedisDao[User] {

  override protected val prefix: String = "user"
  override protected implicit val jsonFormat: JsonFormat[User] = User.userJsonFormat

  def findByEmail(email: String): Option[User] = {
    None
//    withRedis { client =>
//      client.get[User](s"user:$email")
//    }
  }

}
