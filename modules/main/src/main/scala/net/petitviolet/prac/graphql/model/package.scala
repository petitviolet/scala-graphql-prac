package net.petitviolet.prac.graphql

package object model {
  case class Id[A](value: String) extends AnyVal

  trait Entity {
    def id: Id[_]

    override def hashCode: Int = 31 * id.value.##

    override def equals(obj: Any): Boolean = obj match {
      case that: Entity => this.canEqual(that) && this.id.value == that.id.value
      case _            => false
    }

    def canEqual(other: Any): Boolean = other.getClass == this.getClass
  }

}
