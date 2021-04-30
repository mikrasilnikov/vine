name := "PreviewsDownloader2"
version := "0.1"
scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.xerial" % "sqlite-jdbc" % "3.34.0",
  "io.scalac" %% "zio-slick-interop" % "0.3.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.typelevel" %% "cats-parse" % "0.3.2",
  "org.scala-lang.modules" %% "scala-parallel-collections" % "1.0.2",
  "dev.zio" %% "zio" % "1.0.6",
  "dev.zio" %% "zio-macros" % "1.0.6",
  "dev.zio" %% "zio-test"     % "1.0.6" % "test",
  "dev.zio" %% "zio-test-sbt"  % "1.0.6" % "test"
)

scalacOptions += "-Ymacro-annotations"