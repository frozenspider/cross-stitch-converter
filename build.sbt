name           := "cross-stitch-converter"
val prettyName =  "Cross-stitch Converter"
version        := "0.1-SNAPSHOT"
homepage       := Some(url("https://github.com/frozenspider/cross-stitch-converter"))
scalaVersion   := "2.12.6"

// Show tests duration and full stacktrace on test errors
testOptions in Test += Tests.Argument("-oDF")

sourceManaged            := baseDirectory.value / "src_managed"
sourceManaged in Compile := baseDirectory.value / "src_managed" / "main" / "scala"
sourceManaged in Test    := baseDirectory.value / "src_managed" / "test" / "scala"

lazy val root = (project in file("."))
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      "prettyName" -> prettyName,
      version,
      homepage,
      "fullPrettyName" -> (prettyName + " v" + version.value)
    ),
    buildInfoOptions ++= Seq(
      BuildInfoOption.BuildTime
    ),
    buildInfoPackage          := "org.fs.embroidery",
    buildInfoUsePackageAsPath := true
  )

resolvers += "jitpack" at "https://jitpack.io"

libraryDependencies ++= Seq(
  // UI
  "org.scala-lang.modules"    %% "scala-swing"              % "2.0.3",
  "hu.kazocsaba"              %  "image-viewer"             % "1.2.3",
  // Logging
  "org.slf4s"                 %% "slf4s-api"                % "1.7.25",
  "org.slf4j"                 %  "jcl-over-slf4j"           % "1.7.25",
  "ch.qos.logback"            %  "logback-classic"          % "1.1.2",
  // Other
  "com.github.frozenspider"   %% "fs-common-utils"          % "0.1.3",
  "org.apache.commons"        %  "commons-lang3"            % "3.4",
  "commons-io"                %  "commons-io"               % "2.6",
  "com.github.nscala-time"    %% "nscala-time"              % "2.16.0",
  "com.typesafe"              %  "config"                   % "1.3.2",
  // Test
  "junit"                     %  "junit"                    % "4.12"  % "test",
  "org.scalactic"             %% "scalactic"                % "3.0.4" % "test",
  "org.scalatest"             %% "scalatest"                % "3.0.4" % "test"
)
