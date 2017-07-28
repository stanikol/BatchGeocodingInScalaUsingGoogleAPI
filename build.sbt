name := "BatchGeocodingInScalaUsingGoogleAPI"

version := "1.0"

scalaVersion := "2.12.2"

lazy val akkaHttpVersion = "10.0.9"
lazy val playVersion     = "2.6.2"

libraryDependencies ++= Seq(
  "com.typesafe.play" %% "play-json" % playVersion,
  "com.typesafe.play" %% "anorm" % "2.5.3",
  "mysql" % "mysql-connector-java" % "5.1.40",
  "org.postgresql" % "postgresql" % "42.1.3",
  "com.typesafe.akka" %% "akka-http"         % akkaHttpVersion
)
