val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.0"

enablePlugins(AkkaGrpcPlugin)

name := "shopping-order-service"
version := "1.0"

organization := "com.lightbend.akka.samples"
organizationHomepage := Some(url("https://akka.io"))
licenses := Seq(("CC0", url("http://creativecommons.org/publicdomain/zero/1.0")))

Compile / scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint")
Compile / javacOptions ++= Seq("-Xlint:unchecked", "-Xlint:deprecation")

Test / parallelExecution := false
Test / testOptions += Tests.Argument("-oDF")
Test / logBuffered := false

scalaVersion := "2.13.3"
libraryDependencies ++= Seq(
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % AkkaVersion,
    "com.typesafe.akka" %% "akka-discovery" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test,
    "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
    "org.scalatest" %% "scalatest" % "3.1.0" % Test)

run / fork := false
Global / cancelable := false // ctrl-c
