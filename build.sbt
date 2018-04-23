import sbt.Keys.{name, _}

// Multi project build file.  For val xxx = project, xxx is the name of the project and base dir
lazy val commonSettings = Seq(
	organization := "com.jgibbons",
	version := "1.0.1",
	scalaVersion := "2.12.4",
	test in assembly := {},
	assemblyMergeStrategy in assembly := {
		case "logback.xml" => MergeStrategy.discard
		case "application.conf" => MergeStrategy.discard
		case "resources.conf" => MergeStrategy.discard
		case x if x.endsWith("io.netty.versions.properties") => MergeStrategy.last
		case PathList("org", "apache", "logging", "log4j", xs@_*) => MergeStrategy.discard
		case PathList("org", "slf4j", xs@_*) => MergeStrategy.last
		case x => (assemblyMergeStrategy in assembly).value(x)
	},
	assemblyExcludedJars in assembly := {
		val cp = (fullClasspath in assembly).value
		cp filter { d => (d.data.getName == "slf4j-log4j12-1.7.25.jar") ||
			(d.data.getName == "log4j-1.2.17.jar")}
	},
	libraryDependencies ++= Dependencies.commonSettingsDependencies
)

lazy val kafkaglance = (project in file("kafkaglance")).
	settings(commonSettings: _*).
	settings(
		name := "kafkaglance",
		assemblyJarName in assembly := "kafka-glance.jar",
		mainClass in assembly := Some("com.jgibbons.kglance.KafkaGlance"),
		libraryDependencies ++= Dependencies.kafkaglanceDependencies
	)


lazy val jpgkafkaglance = (project in file(".")).
	aggregate(kafkaglance)




