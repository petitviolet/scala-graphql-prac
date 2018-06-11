package net.petitviolet.prac.graphql.sample

import sangria.macros.derive
import sangria.schema._

/**
 * {{{
 * query MyQuery {
 *   all {
 *     __typename
 *     id
 *     name
 *     ...on Dog {
 *       kind
 *     }
 *     ...on Cat {
 *       color
 *     }
 *   }
 * }
 * }}}
 */
object UnionInterfaceSampleApp extends SampleApp(AnimalGraphQLServer)

private object AnimalGraphQLServer extends GraphQLServerBase {
  override type Ctx = UnionInterfaceSampleSchema.AnimalRepository

  override protected def schema = UnionInterfaceSampleSchema.schema

  override protected def context = new UnionInterfaceSampleSchema.AnimalRepository()
}

private object UnionInterfaceSampleSchema {
  trait Animal {
    def id: String
    def name: String
  }
  case class Dog(id: String, name: String, kind: String) extends Animal
  case class Cat(id: String, name: String, color: Color) extends Animal

  sealed abstract class Color(val rgb: String)
  object Color {
    case object White extends Color("#FFFFFF")
    case object Black extends Color("#000000")
    case object Brown extends Color("#A52A2A")
  }

  class AnimalRepository {
    import scala.collection.mutable
    private val data: mutable.Seq[Animal] = mutable.ListBuffer(
      Dog("dog-1", "alice", "golden"),
      Dog("dog-2", "bob", "Chihuahua"),
      Cat("cat-1", "charlie", Color.Brown)
    )

    def findAll: Seq[Animal] = data.toList

    def findById(id: String): Option[Animal] = data.find { a =>
      a.id == id
    }
  }

  lazy val animalInterface: InterfaceType[Unit, Animal] = InterfaceType[Unit, Animal](
    "Animal",
    "animal interface",
    fields[Unit, Animal](
      Field("id", StringType, resolve = ctx => ctx.value.id),
      Field("name", StringType, resolve = ctx => ctx.value.name)
    )
  )

  lazy val animalUnionType = UnionType[Unit]("AnimalUnion", types = dogType :: catType :: Nil)

  lazy val dogType = derive.deriveObjectType[Unit, Dog](
    derive.Interfaces[Unit, Dog](animalInterface)
  )

  implicit lazy val colorEnum = derive.deriveEnumType[Color]()

  lazy val catType = derive.deriveObjectType[Unit, Cat](
    derive.Interfaces[Unit, Cat](animalInterface)
  )

  private object args {}

  lazy val animalQuery: ObjectType[AnimalRepository, Unit] = {
    ObjectType.apply(
      "AnimalQuery",
      fields[AnimalRepository, Unit](
        Field("all", ListType(animalInterface), resolve = ctx => ctx.ctx.findAll)
      )
    )
  }
  lazy val schema: Schema[AnimalRepository, Unit] =
    Schema(animalQuery, additionalTypes = animalUnionType.types)
}
