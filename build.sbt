import java.io.File
import java.util.Date

import com.typesafe.sbt.packager.docker.DockerChmodType
import sbt.Keys.{developers, scmInfo}
import sbt.url

name := "session_manager_example"


inThisBuild(
  Seq(
    scalaVersion := "2.12.9",
    // Needed for our fork of skuber
    resolvers += Resolver.bintrayRepo("jroper", "maven"), // TODO: Remove once skuber has the required functionality
    // Needed for the fixed HTTP/2 connection cleanup version of akka-http
    resolvers += Resolver.bintrayRepo("akka", "snapshots"), // TODO: Remove once we're switching to akka-http 10.1.11
  )
)  


val ProtobufVersion = "3.9.0"
val GrpcJavaVersion = "1.22.1"
val AkkaVersion = "2.5.25"
val AkkaHttpVersion = "10.1.10+124-779795c4" // TODO: Remove once we're switching to akka-http 10.1.11

def common: Seq[Setting[_]] = Seq(
  headerMappings := headerMappings.value ++ Seq(
      de.heikoseeberger.sbtheader.FileType("proto") -> HeaderCommentStyle.cppStyleLineComment,
      de.heikoseeberger.sbtheader.FileType("js") -> HeaderCommentStyle.cStyleBlockComment
    ),
  // Akka gRPC adds all protobuf files from the classpath to this, which we don't want because it includes
  // all the Google protobuf files which are already compiled and on the classpath by ScalaPB. So we set it
  // back to just our source directory.
  PB.protoSources in Compile := Seq(),
  PB.protoSources in Test := Seq(),
  // Akka gRPC overrides the default ScalaPB setting including the file base name, let's override it right back.
  akkaGrpcCodeGeneratorSettings := Seq(),
  excludeFilter in headerResources := HiddenFileFilter || GlobFilter("reflection.proto")
)



lazy val proxyDockerBuild = settingKey[Option[(String, Option[String])]](
  "Docker artifact name and configuration file which gets overridden by the buildProxy command"
)

def dockerSettings: Seq[Setting[_]] = Seq(
  proxyDockerBuild := None,
  dockerUpdateLatest := true,
  dockerRepository := sys.props.get("docker.registry"),
  dockerUsername := sys.props.get("docker.username").orElse(Some("sm")).filter(_ != ""),
  dockerAlias := {
    val old = dockerAlias.value
    proxyDockerBuild.value match {
      case Some((dockerName, _)) => old.withName(dockerName)
      case None => old
    }
  },
  dockerAliases := {
    val old = dockerAliases.value
    val single = dockerAlias.value
    // If a tag is explicitly configured, publish that, otherwise if it's a snapshot, just publish latest, otherwise,
    // publish both latest and the version
    sys.props.get("docker.tag") match {
      case some @ Some(_) => Seq(single.withTag(some))
      case _ if isSnapshot.value => Seq(single.withTag(Some("latest")))
      case _ => old
    }
  },
  // For projects that we publish using Docker, disable the generation of java/scaladocs
  publishArtifact in (Compile, packageDoc) := false
)



lazy val `java-session-manager` = (project in file("samples/java-session-manager"))
  .enablePlugins(AkkaGrpcPlugin, AssemblyPlugin, JavaAppPackaging, DockerPlugin)
  .settings(
    name := "java-session-manager",
    dockerSettings,
    dockerBaseImage := "adoptopenjdk/openjdk8",
    mainClass in Compile := Some("samples.sm.Main"),
    akkaGrpcGeneratedLanguages := Seq(AkkaGrpc.Java),
	libraryDependencies ++= Seq(
		"io.cloudstate" % "cloudstate-java-support" % "0.4.3",
        "com.google.protobuf" % "protobuf-java" % ProtobufVersion % "protobuf",
        "com.google.protobuf" % "protobuf-java-util" % ProtobufVersion,
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
	),
    PB.targets in Compile := Seq(
        PB.gens.java -> (sourceManaged in Compile).value
      ),
    javacOptions in Compile ++= Seq("-encoding", "UTF-8", "-source", "1.8", "-target", "1.8"),
    mainClass in assembly := (mainClass in Compile).value,
    assemblyJarName in assembly := "java-session-manager.jar",
    test in assembly := {}
  )

lazy val `akka-client` = (project in file("samples/akka-client"))
  .enablePlugins(AkkaGrpcPlugin)
  .settings(
    common,
    name := "akka-client",
    fork in run := true,
    libraryDependencies ++= Seq(
        // Remove these explicit gRPC/netty dependencies once akka-grpc 0.7.1 is released and we've upgraded to using that
        "io.grpc" % "grpc-netty-shaded" % GrpcJavaVersion,
        "io.grpc" % "grpc-core" % GrpcJavaVersion,
        "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
        "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
        "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http-core" % AkkaHttpVersion,
        "com.typesafe.akka" %% "akka-http2-support" % AkkaHttpVersion,
        "com.google.protobuf" % "protobuf-java" % ProtobufVersion % "protobuf",
        "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf"
      ),
    PB.protoSources in Compile ++= {
      val baseDir = (baseDirectory in ThisBuild).value / "samples/java-session-manager/src/main"
      Seq(baseDir / "proto")
    }
  )






