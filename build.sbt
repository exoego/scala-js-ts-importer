import sbt.Keys.{ artifactPath, resolvers }

inThisBuild(Def.settings(
  organization := "org.scalajs.tools",
  version := "0.1-SNAPSHOT",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-encoding", "utf8"
  )
))

val `scala-js-ts-importer` = project.in(file("."))
  .settings(
    resolvers += "jitpack" at "https://jitpack.io",
    description := "TypeScript importer for Scala.js",
    scalacOptions += "-P:scalajs:sjsDefinedByDefault",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "0.9.8",
      "blog.codeninja" % "scala-js-vue" % "2.4.2",
      "org.scala-lang.modules" %%% "scala-parser-combinators" % "1.1.2",
      "net.exoego" %%% "scala-js-nodejs-v12" % "0.9.1" % Test, 
      "org.scalatest" %%% "scalatest" % "3.1.0" % Test
    ),
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= { 
      _.withSourceMap(false)
        .withModuleKind(ModuleKind.CommonJSModule)
    },
    Seq(fastOptJS, fullOptJS) map { packageJSKey =>
      artifactPath in (Compile, packageJSKey) := (resourceDirectory in Compile).value / (moduleName.value + "-opt.js")
    }
  )
  .enablePlugins(ScalaJSPlugin)

