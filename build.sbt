// Targets Scala3 LTS
val scala3Version = "3.3.5"

val fs2Version = "3.12.0"
val catsEffectVersion = "3.6.0"
val munitVersion = "1.0.4"
val munitCatsEffectVersion = "2.1.0"

lazy val examples = project
  .settings(
    name := "fs2-backpressure-sensor-examples",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,
  ).dependsOn(root)


lazy val root = project
  .in(file("."))
  .settings(
    name := "fs2-backpressure-sensor",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test
    )
  )
