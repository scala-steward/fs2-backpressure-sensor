import xerial.sbt.Sonatype.sonatypeCentralHost

// Targets Scala3 LTS
val scala3Version = "3.3.5"

val fs2Version = "3.12.0"
val catsEffectVersion = "3.6.0"
val munitVersion = "1.0.4"
val munitCatsEffectVersion = "2.1.0"

inThisBuild(List(
  scalaVersion := scala3Version,
  organization := "com.github.nivox",
  homepage := Some(url("https://github.com/nivox/fs2-backpressure-sensor")),
  licenses := List(License.MIT),
))

ThisBuild / sonatypeCredentialHost := sonatypeCentralHost

lazy val root = project
  .in(file("."))
  .settings(
    name := "fs2-backpressure-sensor",
    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test
    )
  )
