val liftVersion = settingKey[String]("Lift Web Framework full version number")
val liftEdition = settingKey[String]("Lift Edition (such as 2.6 or 3.0)")

name := "Lift LDAP"

organization := "net.liftmodules"

version := "1.6.0-SNAPSHOT"

liftVersion := "3.5.0-SNAPSHOT"

liftEdition := (liftVersion apply { _.substring(0,3) }).value

moduleName := name.value + "_" + liftEdition.value

crossScalaVersions := Seq("2.13.3", "2.12.11", "2.11.12")
scalaVersion := crossScalaVersions.value.head

scalacOptions ++= Seq("-unchecked", "-deprecation")

resolvers ++= Seq(
  DefaultMavenRepository,
  Resolver.sonatypeRepo("public")
)

libraryDependencies ++= Seq(
  "net.liftweb"     %% "lift-mapper"    % liftVersion.value % Provided,
  "ch.qos.logback"  % "logback-classic" % "1.2.3"           % Provided,
  "log4j"           % "log4j"           % "1.2.17"          % Provided
)

libraryDependencies ++= Seq(
  "org.specs2"                  %% "specs2-core"          % "4.9.4"      % Test,
  "org.apache.directory.server" % "apacheds-core"         % "2.0.0.AM26" % Test,
  "org.apache.directory.server" % "apacheds-server-integ" % "2.0.0.AM26" % Test,
  "org.apache.directory.server" % "apacheds-core-integ"   % "2.0.0.AM26" % Test
)

fork in Test := true
