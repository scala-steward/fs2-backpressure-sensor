// Scala versions
val scala3Version = "3.3.5"
val scala2Version = "2.13.16"
val targetJavaVersion = "21"

val fs2Version = "3.12.0"
val catsEffectVersion = "3.6.0"
val munitVersion = "1.0.4"
val munitCatsEffectVersion = "2.1.0"

inThisBuild(
  List(
    scalaVersion := scala3Version,
    crossScalaVersions := Seq(scala3Version, scala2Version),
    organization := "io.github.nivox",
    homepage := Some(url("https://github.com/nivox/fs2-backpressure-sensor")),
    licenses := List(License.MIT),
    versionScheme := Some("early-semver"),
    developers := List(
      Developer(
        id = "nivox",
        name = "Andrea Zito",
        email = "zito.andrea@gmail.com",
        url = url("https://nivox.github.io")
      )
    )
  )
)

lazy val root = project
  .in(file("."))
  .settings(
    name := "fs2-backpressure-sensor",
    scalaVersion := scala3Version,
    crossScalaVersions := Seq(scala3Version, scala2Version),
    
    // Common settings for all Scala versions
    libraryDependencies ++= Seq(
      "co.fs2" %% "fs2-core" % fs2Version,
      "org.scalameta" %% "munit" % munitVersion % Test,
      "org.typelevel" %% "munit-cats-effect" % munitCatsEffectVersion % Test,
      "org.typelevel" %% "cats-effect-testkit" % catsEffectVersion % Test
    ),
    
    // Set Java 21 as target
    javacOptions ++= Seq("-source", targetJavaVersion, "-target", targetJavaVersion),
    
    // Scala 2 specific settings
    scalacOptions ++= (if (scalaVersion.value.startsWith("2.")) 
      Seq(
        "-Xsource:3",
        "-Ymacro-annotations",
        s"-target:jvm-${targetJavaVersion}"
      ) 
    else 
      Seq(s"-Xtarget:${targetJavaVersion}")
    )
  )
