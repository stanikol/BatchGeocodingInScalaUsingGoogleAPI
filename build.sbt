name := "patstatAddressParser"

version := "1.0"

scalaVersion := "2.11.11"


libraryDependencies ++= Seq(
  "com.typesafe.play" %% "anorm" % "2.5.2",
  "com.typesafe.play" %% "play-ws" % "2.5.15",
  "com.typesafe.play" %% "play-json" % "2.5.15",
  "mysql" % "mysql-connector-java" % "5.1.40",
  "org.postgresql" % "postgresql" % "42.1.3",
  "com.github.nikita-volkov" % "sext" % "0.2.4"
)
