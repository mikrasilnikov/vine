name := "PreviewsDownloader2"
version := "0.1"
scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.xerial" % "sqlite-jdbc" % "3.34.0",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.typelevel" %% "cats-parse" % "0.3.2",
  "dev.zio" %% "zio" % "1.0.6",
  "dev.zio" %% "zio-nio" % "1.0.0-RC10",
  "dev.zio" %% "zio-nio-core" % "1.0.0-RC10",
  "dev.zio" %% "zio-macros" % "1.0.6",
  "dev.zio" %% "zio-test"     % "1.0.6" % "test",
  "dev.zio" %% "zio-test-sbt"  % "1.0.6" % "test",
  "com.h2database" % "h2" % "1.4.200" % "test",
  "net.java.dev.jna" % "jna" % "5.8.0",
  "net.java.dev.jna" % "jna-platform" % "5.8.0",
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.1",
  "io.circe" %% "circe-core" % "0.13.0",
  "io.circe" %% "circe-generic" % "0.13.0",
  "io.circe" %% "circe-parser" % "0.13.0",
  "net.ruippeixotog" %% "scala-scraper" % "2.2.1",
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)

scalacOptions += "-Ymacro-annotations"

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")