name := "PreviewsDownloader2"
version := "0.1"
scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "com.h2database" % "h2" % "1.4.200",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.xerial" % "sqlite-jdbc" % "3.34.0",
  "org.typelevel" %% "cats-parse" % "0.3.2",
  "org.typelevel" %% "jawn-parser" % "1.0.0",
  "org.typelevel" %% "jawn-ast" % "1.0.0",
  "dev.zio" %% "zio-test"     % "1.0.6" % "test",
  "dev.zio" %% "zio-test-sbt"  % "1.0.6" % "test"
)

libraryDependencies += "org.scalactic" %% "scalactic" % "3.2.7"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.7" % "test"