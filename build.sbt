import AssemblyKeys._
assemblySettings

test in assembly := {}

name := "Sampler"

version := "0.0.8"

organization := "ahvla"

resolvers ++= Seq(
	"Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
	"Sonatype OSS Releases" at "http://oss.sonatype.org/content/repositories/releases/",
	"Sonatype OSS Snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"
)

libraryDependencies ++= Seq(
	"junit" % "junit" % "4.8" % "test->default",
	"org.specs2" %% "specs2" % "1.13" % "test",
	"org.mockito" % "mockito-all" % "1.9.0" %"test->default",
	"com.typesafe" % "config" % "0.4.1",
	"com.typesafe.akka" %% "akka-actor" % "2.1.4", 
	"com.typesafe.akka" %% "akka-remote" % "2.1.4",
	"com.typesafe.akka" %% "akka-cluster-experimental" % "2.1.4",
	"com.typesafe.akka" %% "akka-slf4j" % "2.1.4", 
	"org.apache.commons" % "commons-math3" % "3.0",
	"ch.qos.logback" % "logback-classic" % "1.0.12",
	"com.amazonaws" % "aws-java-sdk" % "1.4.0.1",
	"javasysmon" % "javasysmon" % "0.3.3" from "http://cloud.github.com/downloads/jezhumble/javasysmon/javasysmon-0.3.3.jar"
)

retrieveManaged := false

scalaVersion := "2.10.1"
