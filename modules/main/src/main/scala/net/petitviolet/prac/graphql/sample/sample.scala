package net.petitviolet.prac.graphql.sample

import sangria.macros.derive
import sangria.marshalling._
import sangria.schema._

object sample extends SampleApp(GraphQLServer)

private object GraphQLServer extends GraphQLServerBase {
  override type Ctx = SchemaSample.MyObjectRepository

  override protected def schema = SchemaSample.schema

  override protected def context = new SchemaSample.MyObjectRepository()

}

private object SchemaSample {
  case class MyObject(id: Long, name: String)

  class MyObjectRepository {

    import scala.collection.mutable

    private val data: mutable.Map[Long, MyObject] = mutable.LinkedHashMap(
      1L -> MyObject(1, "alice"),
      2L -> MyObject(2, "bob"),
    )

    def findAll: Seq[MyObject] = data.values.toList

    def findById(id: Long): Option[MyObject] = data get id

    def store(obj: MyObject): MyObject = {
      data += (obj.id -> obj)
      obj
    }

    def create(name: String): MyObject = {
      val id = data.keys.max + 1
      store(MyObject(id, name))
    }
  }

  val myObjectType: ObjectType[Unit, MyObject] = derive.deriveObjectType[Unit, MyObject]()

  lazy val myQuery: ObjectType[MyObjectRepository, Unit] = {
    ObjectType.apply(
      "MyQuery",
      fields[MyObjectRepository, Unit](
        {
          val idArg = Argument("id", LongType)
          Field(
            "find_by_id",
            OptionType(myObjectType),
            arguments = idArg :: Nil,
            resolve = ctx => ctx.ctx.findById(ctx.arg(idArg))
          )
        },
        Field("all", ListType(myObjectType), resolve = ctx => ctx.ctx.findAll),
      )
    )
  }

  val myObjectInputType: InputObjectType[MyObject] =
    InputObjectType[MyObject]("MyObjectInput",
                              List(
                                InputField("id", LongType),
                                InputField("name", StringType)
                              ))

  implicit val myObjectInput: FromInput[MyObject] = new FromInput[MyObject] {
    override val marshaller: ResultMarshaller = CoercedScalaResultMarshaller.default

    override def fromResult(node: marshaller.Node): MyObject = {
      val m = node.asInstanceOf[Map[String, Any]]
      MyObject(m("id").asInstanceOf[Long], m("name").asInstanceOf[String])
    }
  }

  lazy val myMutation: ObjectType[MyObjectRepository, Unit] = {
    ObjectType.apply(
      "MyMutation",
      fields[MyObjectRepository, Unit](
        {
          val inputMyObject = Argument("my_object", myObjectInputType)
          Field(
            "store",
            arguments = inputMyObject :: Nil,
            fieldType = myObjectType,
            resolve = c => c.ctx.store(c arg inputMyObject)
          )
        }, {
          val inputName = Argument("name", StringType)
          Field(
            "create",
            arguments = inputName :: Nil,
            fieldType = myObjectType,
            resolve = c => c.ctx.create(c arg inputName)
          )
        }
      )
    )
  }

  lazy val schema: Schema[MyObjectRepository, Unit] = Schema(myQuery, Some(myMutation))
}
