package net.petitviolet.prac.graphql

import sangria.execution.deferred.HasId
import sangria.schema._

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
}
