import ml.dmlc.xgboost4j.scala.spark.{TrackerConf, XGBoostClassifier, XGBoostClassificationModel}
import org.apache.spark.ml.evaluation.BinaryClassificationEvaluator
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.Pipeline
import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._
spark.sparkContext.setLogLevel("FATAL")

// LOAD DATA
val save_path_train = s"../../external/churn/data/train/*"
val save_path_valid = s"../../external/churn/data/valid/*"
val df_train = spark.read.parquet(save_path_train)
val df_valid = spark.read.parquet(save_path_valid)

def getStats(Data: DataFrame, Target: String) = {
    val nrows = Data.count
    val ncols = Data.columns.length
    val props = Data.select(Target).groupBy().agg(avg(col(Target))).take(1)(0).get(0).toString.toFloat
Array(nrows, ncols, props)}

val Target = "target_userIgnr"

val Train = df_train.where($"useID2".isNotNull && $"ratio_befor" < lit(1.0) && col(Target).isNotNull).na.fill(0)
val Valid = df_valid.where($"useID2".isNotNull && $"ratio_befor" < lit(1.0) && col(Target).isNotNull).na.fill(0)

// Train.printSchema
// Valid.printSchema

// GET STATS
val statTrain = getStats(Train, Target)
val statValid = getStats(Valid, Target)
Train.groupBy("current_date").agg(count($"useID1"), avg(col(Target))).orderBy("current_date").show
Valid.groupBy("current_date").agg(count($"useID1"), avg(col(Target))).orderBy("current_date").show

// SET PIPELINE PARAMS
val exclude_name = "current_date,useID1,closest_auditDate,interval_befor,useID2,ratio_after,sum_showCont_after,sum_igndCont_after,interval_after,userId,useID,target_userLost".split(",") ++ Array(Target)
val exclude_type = Array(StringType, TimestampType, DateType)
val main_cols = Train.schema.fields.filterNot(f => exclude_type contains f.dataType).filterNot(f => exclude_name contains f.name).map(x => x.name)
println("MAIN_COLS_ARRAY_SIZE: " + main_cols.length)

val pos_wei = (1-statTrain(2))/statTrain(2)
val parallelizm = 4
val xgbParam: Map[String, Any] = Map(
    "missing" -> 0,
    "objective" -> "binary:logistic",
    "eval_metric" ->  "logloss",
    "eta" -> 0.01,
    "max_depth" -> 3,
    "num_round" -> 200,
    "subsample" -> 0.7,
    "min_child_weight" -> 100,
    "scale_pos_weight" -> pos_wei.toInt,
    "num_workers" -> parallelizm,
    "verbosity" -> 1,
    "tracker_conf" -> TrackerConf(60*60*1000, "scala"),
    "kill_spark_context_on_worker_failure" -> false
)


// TRAIN MODEL PIPELINE
val vectorAssembler = new VectorAssembler().setInputCols(main_cols).setOutputCol("features")
val xgbClassifier = new XGBoostClassifier(xgbParam).setFeaturesCol("features").setLabelCol(Target)
val pipeModel = new Pipeline().setStages(Array(vectorAssembler, xgbClassifier))

val xgbPipelineModel = pipeModel.fit(Train)
xgbPipelineModel.write.overwrite().save(s"../../external/churn/model/xgb_pipeline")
xgbPipelineModel.stages(1).asInstanceOf[XGBoostClassificationModel].nativeBooster.saveModel("../../external/churn/model/xgb_native.json")

val v0 = udf((v: org.apache.spark.ml.linalg.Vector) => v.toArray(0))
val v1 = udf((v: org.apache.spark.ml.linalg.Vector) => v.toArray(1))

(
    xgbPipelineModel
    .transform(Train)
    .withColumn("score", v1($"probability"))
    .select("current_date","useID1",Target, "score")
    .coalesce(1)
    .write
    .mode("overwrite")
    .option("header", "true")
    .option("delimiter","\t")
    .csv("../../external/churn/data/score_train")

)
(
    xgbPipelineModel
    .transform(Valid)
    .withColumn("score", v1($"probability"))
    .select("current_date","useID1",Target, "score")
    .coalesce(1)
    .write
    .mode("overwrite")
    .option("header", "true")
    .option("delimiter","\t")
    .csv("../../external/churn/data/score_valid")
)

def printMetric(spark: org.apache.spark.sql.SparkSession, data: DataFrame, label: String) = {
    val evaluator = new BinaryClassificationEvaluator().setLabelCol(label).setRawPredictionCol("probability").setMetricName("areaUnderROC")
    val result = xgbPipelineModel.transform(data).withColumn("score", v1($"probability"))
    val rocauc = evaluator.evaluate(result)
    println("ROCAUC: " + rocauc.toString)
    val query =s"""
      |SELECT --*
      | avg(${label}) as target_avg,
      | avg(CASE WHEN t.nn <= t.cnt*0.01 THEN t.${label} ELSE Null END) as q001,
      | avg(CASE WHEN t.nn <= t.cnt*0.05 THEN t.${label} ELSE Null END) as q005,
      | avg(CASE WHEN t.nn <= t.cnt*0.10 THEN t.${label} ELSE Null END) as q010,
      | avg(CASE WHEN t.nn <= t.cnt*0.15 THEN t.${label} ELSE Null END) as q015
      |FROM
      |   (SELECT ${label}, score, row_number() over(order by score desc) as nn, count(*) over() as cnt FROM RESULT order by score DESC) t
      |""".stripMargin
    result.select(label, "score").createOrReplaceTempView("RESULT")
    spark.sql(query).show()
}

// METRIC ON TRAIN
printMetric(spark, Train, Target)
// METRIC ON VALID
printMetric(spark, Valid, Target)

spark.catalog.clearCache()
spark.close()
spark.stop()