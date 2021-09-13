// See README.md for license details.

ThisBuild / scalaVersion     := "2.12.10"
ThisBuild / version          := "1.2-SNAPSHOT"
ThisBuild / organization     := "com.github.uyiromo"

lazy val nvsit = (project in file("."))
  .settings(
    name := "NVSIT",
    libraryDependencies ++= Seq(
      "edu.berkeley.cs" %% "chisel3" % "3.4.3",
      "edu.berkeley.cs" %% "chiseltest" % "0.3.3" % "test",
      "edu.berkeley.cs" %% "chisel-iotesters" % "1.2.5"
    ),
    scalacOptions ++= Seq(
      "-Xsource:2.11",
      "-language:reflectiveCalls",
      "-deprecation",
      "-feature",
      "-Xcheckinit",
      // Enables autoclonetype2 in 3.4.x (on by default in 3.5)
      //"-P:chiselplugin:useBundlePlugin"
    ),
    //addCompilerPlugin("edu.berkeley.cs" % "chisel3-plugin" % "3.4.3" cross CrossVersion.full),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.full)
  )
