ThisBuild / scalaVersion := "2.13.18"

ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """play-framework-todo""",
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play"       %% "play-slick" % "5.2.0",
      "com.microsoft.sqlserver"  % "mssql-jdbc" % "13.4.0.jre11",
      "org.scalatestplus.play"  %% "scalatestplus-play" % "5.1.0" % Test
    )
  )