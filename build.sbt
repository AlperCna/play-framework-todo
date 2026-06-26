ThisBuild / scalaVersion := "2.13.18"

ThisBuild / version := "1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)
  .settings(
    name := """play-framework-todo""",
    libraryDependencies ++= Seq(
      guice,
      "com.typesafe.play"       %% "play-slick"            % "5.2.0",
      "org.postgresql"           % "postgresql"             % "42.7.3",
      "com.github.tminglei"     %% "slick-pg"              % "0.21.1",
      "org.pac4j"               %% "play-pac4j"            % "12.0.0-PLAY2.9",
      "org.pac4j"                % "pac4j-http"            % "6.0.0",
      "org.apache.shiro"         % "shiro-core"            % "1.13.0",
      "org.scalatestplus.play"  %% "scalatestplus-play"    % "5.1.0" % Test
    ),
    // pac4j, Jackson 2.16'yi transitive cekiyor; Play 2.9'un jackson-module-scala'si
    // 2.14.x ister (uyumsuzluk Akka/Play serializasyonunu acilista patlatir).
    // Jackson cekirdegini Play'in surumune sabitleyerek hizalariz.
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-databind"    % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-core"        % "2.14.3",
      "com.fasterxml.jackson.core" % "jackson-annotations" % "2.14.3"
    )
  )