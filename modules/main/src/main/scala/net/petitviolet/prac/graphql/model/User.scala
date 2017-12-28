package net.petitviolet.prac.graphql.model

case class User(id: Id[User], name: User.Name, email: User.Email) extends Entity {

}

object User {
  case class Name(value: String) extends AnyVal
  case class Email(value: String) extends AnyVal
}
