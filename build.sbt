name := "vine"
version := "1.0.0"
scalaVersion := "2.13.5"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.typesafe.slick" %% "slick" % "3.3.3",
  "org.xerial" % "sqlite-jdbc" % "3.34.0",
  "dev.zio" %% "zio-logging-slf4j" % "0.5.10",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "org.codehaus.janino" % "janino" % "3.1.4",
  "org.typelevel" %% "cats-parse" % "0.3.2",
  "dev.zio" %% "zio" % "1.0.9",
  "dev.zio" %% "zio-nio" % "1.0.0-RC10",
  "dev.zio" %% "zio-nio-core" % "1.0.0-RC10",
  "dev.zio" %% "zio-macros" % "1.0.9",
  "dev.zio" %% "zio-test"     % "1.0.9" % "test",
  "dev.zio" %% "zio-test-sbt"  % "1.0.9" % "test",
  "com.h2database" % "h2" % "1.4.200" % "test",
  "org.fusesource.jansi" % "jansi" % "2.3.2",
  "com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.1",
  "io.circe" %% "circe-core" % "0.13.0",
  "io.circe" %% "circe-generic" % "0.13.0",
  "io.circe" %% "circe-parser" % "0.13.0",
  "net.ruippeixotog" %% "scala-scraper" % "2.2.1",
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)

testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

scalacOptions += "-Ymacro-annotations"

assembly / mainClass := Some("vine.Application")
assembly / assemblyJarName := "vine.jar"

assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "DUMMY.DSA") => MergeStrategy.discard
  case PathList("META-INF", "DUMMY.SF") => MergeStrategy.discard
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case _ => MergeStrategy.first
}
