name := "llr"

version := "1.0"

scalaVersion := "2.10.6"

resolvers++=Seq("Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
  "releases" at "http://oss.sonatype.org/service/local/staging/deploy/maven2/")
  
  libraryDependencies ++= Seq("org.apache.spark" %% "spark-core" % "2.1.0",
  "org.apache.spark" % "spark-mllib_2.10" % "2.1.0")
