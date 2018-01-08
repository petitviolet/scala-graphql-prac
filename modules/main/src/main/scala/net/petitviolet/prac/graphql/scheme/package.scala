package net.petitviolet.prac.graphql

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

import sangria.execution.deferred.HasId
import sangria.schema._
import sangria.validation.Violation

package object scheme {
  private[scheme] def entityType[A] = InterfaceType(
    "entity",
    "entity",
    () =>
      fields[A, dao.Entity](
        Field("id", StringType, Some("id of entity"), resolve = _.value.id)
    )
  )
  private[scheme] implicit def hasId[A <: dao.Entity]: HasId[A, dao.Id] = HasId(_.id)

  implicit val ZonedDateTimeType: ScalarType[ZonedDateTime] = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    def error = new Violation {
      override def errorMessage: String = "ZonedDateTime expected"
    }

    ScalarType[ZonedDateTime](
      "ZonedDateTime",
      description = Some("ZonedDateTime!"),
      coerceOutput = { (zdt, _) =>
        zdt.format(dateTimeFormatter)
      },
      coerceUserInput = { _ =>
        Left(error)
      },
      coerceInput = { _ =>
        Left(error)
      }
    )
  }

}
