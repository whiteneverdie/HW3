name := "xgbChurnOtus"

version := "0.1"

scalaVersion := "2.11.12"


// https://mvnrepository.com/artifact/org.apache.spark/spark-core
libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.5"  //% "provided"

// https://mvnrepository.com/artifact/org.apache.spark/spark-sql
libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.5"   //% "provided"

// https://mvnrepository.com/artifact/org.apache.spark/spark-mllib
libraryDependencies += "org.apache.spark" %% "spark-mllib" % "2.4.5" //% "provided"

// https://mvnrepository.com/artifact/ml.dmlc/xgboost4j
libraryDependencies += "ml.dmlc" % "xgboost4j" % "0.81"

// https://mvnrepository.com/artifact/ml.dmlc/xgboost4j-spark
libraryDependencies += "ml.dmlc" % "xgboost4j-spark" % "0.81"

//mainClass := Some("xgbChurnOtus")
//assemblyMergeStrategy in assembly := {
//  case PathList("META-INF", _*) => MergeStrategy.discard
//  case _ => MergeStrategy.first
//}
