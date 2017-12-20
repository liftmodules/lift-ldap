val liftVersion = settingKey[String]("Lift Web Framework full version number")
val liftEdition = settingKey[String]("Lift Edition (such as 2.6 or 3.0)")

name := "Lift LDAP"

organization := "net.liftmodules"

version := "1.6.0-SNAPSHOT"

liftVersion := "3.0.1"

liftEdition := (liftVersion apply { _.substring(0,3) }).value

moduleName := name.value + "_" + liftEdition.value

crossScalaVersions := Seq("2.12.4", "2.11.12")
scalaVersion := crossScalaVersions.value.head

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers ++= Seq(
  "CB Central Mirror" at "http://repo.cloudbees.com/content/groups/public",
  "Java.net Maven2 Repository" at "http://download.java.net/maven/2/"
)

libraryDependencies ++= Seq(
  "net.liftweb" %% "lift-mapper" % liftVersion.value % "provided",
  "ch.qos.logback" % "logback-classic" % "1.2.3" % "provided",
  "log4j" % "log4j" % "1.2.17" % "provided"
)

libraryDependencies ++= Seq(
  "org.specs2" %% "specs2-core" % "3.9.1" % "test",
  "org.apache.directory.server" % "apacheds-core" % "2.0.0-M24" % "test",
  "org.apache.directory.server" % "apacheds-server-integ" % "2.0.0-M24" % "test",
  "org.apache.directory.server" % "apacheds-core-integ" % "2.0.0-M24" % "test"
)

fork in Test := true