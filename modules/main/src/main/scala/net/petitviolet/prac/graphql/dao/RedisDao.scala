package net.petitviolet.prac.graphql.dao

import com.redis.serialization.Parse
import org.slf4j.LoggerFactory
import spray.json.JsonFormat

trait RedisDao[A <: Entity] {
  protected val prefix: String
  protected implicit def jsonFormat: JsonFormat[A]
  protected val logger = LoggerFactory.getLogger(this.getClass)
  protected def withLogging[T](msg: String)(t: => T): T = {
    val result = t
    logger.info(s"[$prefix]$msg => $result")
    result
  }

  protected implicit val idParse: Parse[Id] = {
    Parse[Id] { bytes =>
      new String(bytes)
    }
  }

  protected implicit val parse: Parse[A] = {
    import spray.json._
    Parse[A] { bytes =>
      val json = new String(bytes)
      jsonFormat.read(json.parseJson)
    }
  }

  def findById(id: Id): Option[A] = {
    withRedis { client =>
      withLogging(s"findById($id)") {
        client.get[A](s"$prefix:id:$id")
      }
    }
  }

  def findAllByIds(ids: Seq[Id]): Seq[A] = {
    withRedis { client =>
      ids.flatMap { id =>
        withLogging(s"findAllByIds: ${ids.mkString(", ")}") {
          client.get[A](s"$prefix:id:$id")
        }
      }
    }
  }

  def findAll: Seq[A] = {
    withRedis { client =>
      client.keys[Id](s"$prefix:id:*").fold(Seq.empty[A]) { keyOpts: Seq[Option[String]] =>
        val keys: Seq[Id] = keyOpts.collect {
          case Some(idWithPrefix) =>
            val Array(_, id) = idWithPrefix.split(":id:")
            id
        }
        withLogging(s"findAll") {
          findAllByIds(keys)
        }
      }
    }
  }

  def create(entity: A): Unit = {
    withRedis { client =>
      withLogging(s"create: $entity") {
        client.set(s"$prefix:id:${entity.id}", jsonFormat.write(entity))
      }
    }
  }

  def update(entity: A): Unit =
    create(entity)
}
