name := "sbt-web3j-generator"

lazy val bintraySettings = Seq(
	bintrayOrganization in ThisBuild := Some("anchormen"),
	bintrayReleaseOnPublish in ThisBuild := false
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
	sbtPlugin := true,
	version := "0.1"
)

lazy val publicationSettings = Seq(
	publishMavenStyle := false
)

lazy val repoSettings = Seq(
	resolvers += Resolver.typesafeRepo("releases")
)

lazy val sbtSettings = Seq(
//	crossSbtVersions := Seq("0.13.17", "1.1.1")
	sbtVersion := "1.1.1"
//	sbtVersion in Global := "1.1.1"
)

lazy val scalaSettings = Seq(
	scalaVersion := "2.12.4"
//	crossScalaVersions := Seq("2.11.11", "2.12.4"),
//	scalaCompilerBridgeSource := {
//		val sv = appConfiguration.value.provider.id.version
//		("org.scala-sbt" % "compiler-interface" % sv % "component").sources
//	}
//	scalaVersion := (CrossVersion partialVersion (sbtVersion in pluginCrossBuild).value match {
//		case Some((0, 13)) => "2.11.11"
//		case Some((1, _))  => "2.12.4"
//		case _             => sys error s"Unhandled sbt version ${(sbtVersion in pluginCrossBuild).value}"
//	})
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
		.settings(publicationSettings)
		.settings(repoSettings)
		.settings(sbtSettings)
		.settings(scalaSettings)
		.settings(testSettings)
