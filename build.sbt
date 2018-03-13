name := "sbt-web3j-generator"

lazy val bintraySettings = Seq(
	bintrayRepository in ThisBuild := "sbt-plugins",
	bintrayOrganization in ThisBuild := Some("anchormen"),
	bintrayReleaseOnPublish in ThisBuild := false,
)

lazy val commonDependencies = Seq(
	libraryDependencies += "org.web3j" % "codegen" % "3.2.0"
)

lazy val javaSettings = Seq(
	javaOptions ++= Seq("-Xms1G", "-Xmx4G", "-XX:+CMSClassUnloadingEnabled"),
	javacOptions ++= Seq(
		"-source", "1.8",
		"-encoding", "UTF-8"
	),
	javacOptions in(Compile, compile) ++= Seq(
		"-target", "1.8",
		"-Xlint:deprecation",
		"-Xlint:unchecked"
	)
)

lazy val pluginSettings = Seq(
	conflictManager := ConflictManager.latestRevision,
	licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
	organization := "nl.anchormen.sbt",
	publishMavenStyle := false,
	sbtPlugin := true,
	version := "0.1.3"
)

lazy val scalaSettings = Seq(
	scalaVersion := "2.12.4"
)

lazy val testSettings = Seq(
	fork in Test := true,
	logBuffered in Test := false,
	parallelExecution in Test := false,
	publishArtifact in Test := false
)

lazy val plugin = project
		.in(file("."))
		.settings(bintraySettings)
		.settings(commonDependencies)
		.settings(javaSettings)
		.settings(pluginSettings)
		.settings(scalaSettings)
		.settings(testSettings)
