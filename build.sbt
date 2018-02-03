name := "scala-graphql-prac"

import sbt.Keys._

val libVersion = "1.0"

val scala = "2.12.4"

val commonDependencies = Seq(
  "org.sangria-graphql" %% "sangria" % "1.3.3",
  "org.sangria-graphql" %% "sangria-spray-json" % "1.0.0",
//  "org.sangria-graphql" %% "sangria-circe" % "1.1.1",
  "com.typesafe.akka" %% "akka-http" % "10.0.10",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.10",
  "com.lihaoyi" %% "sourcecode" % "0.1.3",
  "org.scalatest" %% "scalatest" % "3.0.4" % Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)
def commonSettings(_name: String) = Seq(
  scalaVersion := scala,
  version := libVersion,
  libraryDependencies ++= commonDependencies,
  name := _name,
  trapExit := false,
  scalafmtConfig := Some(file(".scalafmt.conf")),
  scalafmtOnCompile := true,
)

lazy val gaphqlPrac = (project in file("."))
  .settings(commonSettings("graphqlPrac"))

lazy val main = (project in file("modules/main"))
  .enablePlugins(JavaServerAppPackaging)
  .settings(commonSettings("main"))
  .settings(
    packageName in Docker := "graphql-prac",
    version in Docker := libVersion,
    dockerRepository := Some("petitviolet"),
    maintainer in Docker := "petitviolet <mail@petitviolet.net>",
    dockerExposedPorts := List(8080),
//    dockerBaseImage := "openjdk:8-jdk-alpine",
    dockerCmd := List("bin/main")
  )

