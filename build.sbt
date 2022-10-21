import sbt.Keys.{ artifactPath, resolvers }

lazy val scala213 = "2.13.10"
crossScalaVersions in ThisBuild := Seq(scala213)
scalaVersion in ThisBuild := scala213

inThisBuild(Def.settings(
  organization := "org.scalajs.tools",
  version := "0.1-SNAPSHOT",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "utf8"
  )
))

lazy val `scala-js-ts-importer` = project.in(file("."))
  .aggregate(importer, webapp, samples)

lazy val webapp = project.in(file("webapp"))
  .settings(
    resolvers += "jitpack" at "https://jitpack.io",
    description := "TypeScript importer for Scala.js",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "2.3.0"
    ),
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= { 
      _.withSourceMap(false)
    },
    Seq(fastOptJS, fullOptJS) map { packageJSKey =>
      artifactPath in (Compile, packageJSKey) := (resourceDirectory in Compile).value / (moduleName.value + "-opt.js")
    }
  )
  .enablePlugins(ScalaJSPlugin)
  .dependsOn(importer)

lazy val importer = project.in(file("importer"))
  .settings(
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-parser-combinators" % "2.1.1",
      "net.exoego" %%% "scala-js-nodejs-v12" % "0.14.0" % Test,
      "org.scalatest" %%% "scalatest" % "3.2.13" % Test
    ),
    scalaJSLinkerConfig ~= {
      _.withSourceMap(false)
        .withModuleKind(ModuleKind.CommonJSModule)
    },
  )
  .enablePlugins(ScalaJSPlugin)

lazy val samples = project
  .enablePlugins(ScalaJSPlugin)

