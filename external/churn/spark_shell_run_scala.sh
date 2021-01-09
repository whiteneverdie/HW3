## sudo docker exec -it spark-master /bin/bash
## mv /root/.ivy2/jars/com.esotericsoftware.reflectasm_reflectasm-1.07-shaded.jar /root/.ivy2/jars/com.esotericsoftware.reflectasm_reflectasm-1.07.jar

cat /external/churn/src/xgbChurnSaveModel.scala | time /spark/bin/spark-shell --master spark://172.27.1.10:7077 --packages "ml.dmlc:xgboost4j:0.90,ml.dmlc:xgboost4j-spark:0.90" | tee -a "/external/churn/logs/xgb_spark_track_$(date -d "today" +"%Y%m%d%H%M%S").log"