//import com.typesafe.sbt.SbtMultiJvm.multiJvmSettings
//import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm

scalaVersion := "2.12.7"

lazy val akkaVersion = "2.5.17"

lazy val hasher = project
  .in(file("."))
  .settings(
    organization := "com.github.leananeuber",
    name := "akka-actor-party",
    version := "0.0.1",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "com.typesafe.akka" %% "akka-multi-node-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % "3.0.5" % "test",
    )
  )
  // for multi-jvm tests:
//  .settings(multiJvmSettings: _*)
//  .configs(MultiJvm)