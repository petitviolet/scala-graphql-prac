package net.petitviolet.prac.graphql.dao

import com.redis.serialization.Parse
import spray.json.JsonFormat

trait RedisDao[A <: Entity] {
  protected val prefix: String
  protected implicit def jsonFormat: JsonFormat[A]

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
      client.get[A](s"$prefix:$id")
    }
  }

  def findAllByIds(ids: Seq[Id]): Seq[A] = {
    withRedis { client =>
      ids.flatMap { id =>
        client.get[A](s"$prefix:$id")
      }
    }
  }

  def findAll: Seq[A] = {
    withRedis { client =>
      client.keys[Id](s"$prefix:*").fold(Seq.empty[A]) { keyOpts: Seq[Option[Id]] =>
        val keys: Seq[Id] = keyOpts.collect {
          case Some(idWithPrefix) => idWithPrefix.dropWhile { _ != ':' }.tail
        }
        findAllByIds(keys)
      }
    }
  }

  def create(entity: A): Unit = {
    withRedis { client =>
      client.set(s"$prefix:${entity.id}", jsonFormat.write(entity))
    }
  }

  def update(entity: A): Unit =
    create(entity)
}
