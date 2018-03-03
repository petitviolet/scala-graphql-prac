package net.petitviolet.prac.graphql

import java.time.format.DateTimeFormatter
import java.time.{ ZoneId, ZonedDateTime }
import java.util.UUID
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }

import com.redis.{ RedisClient, RedisClientPool }
import com.typesafe.config.ConfigFactory
import spray.json._

package object dao {
  type Id = String
  trait Entity {
    def id: Id
  }
  private[dao] implicit val idJsonFormat: JsonFormat[Id] = {
    DefaultJsonProtocol.StringJsonFormat
  }
  private[dao] implicit val dateTimeFormat: JsonFormat[ZonedDateTime] = {
    val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    new JsonFormat[ZonedDateTime] {
      override def read(json: JsValue): ZonedDateTime = {
        json match {
          case JsString(x) => ZonedDateTime.parse(x, formatter)
          case _           => deserializationError(s"$json is invalid for ZonedDateTime")
        }
      }

      override def write(obj: ZonedDateTime): JsValue = {
        JsString(obj.format(formatter))
      }
    }
  }

  private var id: AtomicInteger = new AtomicInteger(1)
  def generateId: Id = {
    id.getAndIncrement().toString
//    UUID.randomUUID().toString
  }
  private val zoneId = ZoneId.of("Asia/Tokyo")
  def now(): ZonedDateTime = ZonedDateTime.now(zoneId)

  private[dao] lazy val redisClients = {
    val config = ConfigFactory.load()
    val host = config.getString("my.redis.host")
    val port = config.getInt("my.redis.port")
    new RedisClientPool(host, port)
  }
  private[dao] def withRedis[A](ops: (RedisClient => A)) = {
    redisClients.withClient(ops)
  }
}
