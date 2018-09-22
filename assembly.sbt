//
// sbt-assembly configuration
//

val buildOutputPath = file("./_build")

mainClass          in assembly := Some("org.fs.embroidery.CrossStitchConverterMain")
assemblyJarName    in assembly := name.value + ".jar"
assemblyOutputPath in assembly := buildOutputPath / (assemblyJarName in assembly).value

// Discard META-INF files to avoid assembly deduplication errors
assemblyMergeStrategy in assembly := {
  case PathList("META-INF", _ @_*) => MergeStrategy.discard
  case _                           => MergeStrategy.first
}



//
// launch4j task
//

val launch4j = taskKey[Unit](s"Generates Launch4j executable binaries")

launch4j := {
  import net.sf.launch4j.{ Log, Builder }
  import net.sf.launch4j.config._

  val launch4jBasedirString = sys.env.getOrElse("LAUNCH4J_HOME",
    throw new Exception("Please install Launch4j (preferribly v3.11) locally and set LAUNCH4J_HOME env variable") with FeedbackProvidedException)
  val launch4jBasedir = new File(launch4jBasedirString)

  val configurations = Seq(
    ("x86", Jre.RUNTIME_BITS_32),
    ("x64", Jre.RUNTIME_BITS_64)
  )

  ConfigPersister.getInstance.createBlank()
  val conf: Config = ConfigPersister.getInstance.getConfig
  conf.setHeaderType("gui")
  // conf.setIcon(file("./src/main/resources/icons/main.ico"))
  conf.setJar(new File((assemblyJarName in assembly).value))
  conf.setDontWrapJar(true)
  conf.setDownloadUrl("http://java.com/download")

  val cp = new ClassPath
  cp.setMainClass((mainClass in assembly).value.get)
  conf.setClassPath(cp)

  val jre = new Jre
  jre.setMinVersion("1.8.0")
  jre.setJdkPreference(Jre.JDK_PREFERENCE_PREFER_JDK)
  conf.setJre(jre)

  configurations.foreach {
    case (arch, jreRuntimeBits) =>
      conf.setOutfile(buildOutputPath / s"${name.value}_${arch}.exe")
      jre.setRuntimeBits(jreRuntimeBits)
      conf.validate()
      new Builder(Log.getConsoleLog, launch4jBasedir).build()
  }
}


//
// buildDistr task
//

val buildDistr = taskKey[Unit](s"Complete build: assemble a runnable .jar and generate Windows executables")

buildDistr := {
  assembly.value
  launch4j.value
}
