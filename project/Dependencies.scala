import sbt._

object Dependencies {
  val AkkaVersion = "2.5.11" // akkahttp conflicts with 2.5.xx
  val Json4sVersion     = "3.5.3"
  val Avro4sVersion     = "1.8.0"
  //val AkkaStreamVersion = "1.0"
  val ScalaTestVersion  = "3.0.1"
  val ScalaMockVersion = "4.0.0"
  val KafkaVersion 			= "1.0.0"
  val TypesafeConfigVersion = "1.3.1"
  val LogbackVersion = "1.2.3"
  val Log4jBridgeVersion = "1.7.25"
  // seems to have a dependency on 2.4.19, so not using Akka 2.5.4 yet.
  val AkkaHttpVersion = "10.1.0"

  val config = Seq(
    "com.typesafe" % "config" % TypesafeConfigVersion)

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion,
    "com.typesafe.akka" %% "akka-stream" % AkkaVersion, // If you dont include this you are fcked.
    "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test)

  val akkaHttp = Seq(
    "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-testkit" % AkkaHttpVersion % Test)

  val kafka = Seq(
    "org.apache.kafka" %% "kafka" % KafkaVersion  exclude("org.apache.logging.log4j","log4j-api") exclude("org.apache.logging.log4j","log4j-core"),
    //	https://github.com/sksamuel/avro4s
    "com.sksamuel.avro4s" %% "avro4s-core" % Avro4sVersion  exclude("org.apache.logging.log4j","log4j-api") exclude("org.apache.logging.log4j","log4j-core"),
    // https://mvnrepository.com/artifact/org.apache.kafka/kafka-streams
    "org.apache.kafka" % "kafka-streams" % KafkaVersion
  )

  // Multi project build file.  For val xxx = project, xxx is the name of the project and base dir
  val commonSettingsDependencies = Seq(
    "org.scalamock" %% "scalamock" % ScalaMockVersion % Test,
    "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,
    "ch.qos.logback" % "logback-classic" % LogbackVersion,
    "org.slf4j" % "log4j-over-slf4j" % Log4jBridgeVersion
  )

  val jpgcommonDependencies = config

  val svcCommonDependencies = Seq(
  ) ++kafka ++ akka ++ akkaHttp ++ config

  val kafkaglanceDependencies = Seq(
  ) ++svcCommonDependencies
  
}