addSbtPlugin("com.eed3si9n"  % "sbt-assembly"  % "0.14.7")
addSbtPlugin("com.eed3si9n"  % "sbt-buildinfo" % "0.9.0")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// Launch4j
libraryDependencies += ("net.sf.launch4j" % "launch4j" % "3.11")
  .exclude("com.ibm.icu", "icu4j")
  .exclude("abeille", "net.java.abeille")

// Launch4j dependency - required for com.springsource.org.apache.batik
resolvers ++= Seq(
  "SpringSource" at "http://repository.springsource.com/maven/bundles/external",
  "Simulation @ TU Delft" at "http://simulation.tudelft.nl/maven/"
)
