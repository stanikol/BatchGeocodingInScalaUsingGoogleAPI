name := "BatchGeocodingInScalaUsingGoogleAPI"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaHttpVersion = "10.0.9"
lazy val playVersion     = "2.6.2"

libraryDependencies ++= Seq(
  "com.github.scopt" %% "scopt" % "3.5.0",
//  "com.typesafe.play" %% "play-jsonText" % playVersion,
  "com.typesafe.play" %% "play-json" % "2.6.8",
  "com.typesafe.play" %% "anorm" % "2.5.3", //?  "com.typesafe.play" %% "anorm" % "2.5.1"
  "com.typesafe.play" %% "anorm-akka" % "2.5.3", //?  "com.typesafe.play" %% "anorm" % "2.5.1"
  "mysql" % "mysql-connector-java" % "5.1.40",
  "org.postgresql" % "postgresql" % "42.1.3",
//  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "org.apache.sis.core" % "sis-referencing" % "0.7",
  "com.github.nikita-volkov" % "sext" % "0.2.4",
  "com.univocity" % "univocity-parsers" % "2.5.8",
  "com.ibm.icu" % "icu4j" % "59.1"
)

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.7.2"

libraryDependencies += "org.scalactic" %% "scalactic" % "3.0.4"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"


// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http
libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.0-RC2"  //"10.0.11"

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-actor
libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.5.9"

libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % "2.5.9"
// https://mvnrepository.com/artifact/com.typesafe.akka/akka-http-testkit

// https://mvnrepository.com/artifact/com.typesafe.akka/akka-stream
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.5.9"


libraryDependencies ++= Seq(
  "com.typesafe.slick" %% "slick" % "3.2.1",
  "org.slf4j" % "slf4j-nop" % "1.6.4",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.2.1"
)

libraryDependencies += "com.lightbend.akka" %% "akka-stream-alpakka-slick" % "0.17"
