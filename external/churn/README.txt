Основной воркфлоу в notebooks/03_churn_predict_and_analysis.ipynb

Эстиматор src/xgbChurnSaveModel.scala (в процессе отладки в .jar не собирался)

Запускался отправкой кода в spark-shell напрямую в контейнере spark-master (см. spark_shell_run_scala.sh)

На рантайме процесс обучения эстиматора зеркалился в логи (см. logs/)

-- Проблема: импорт полученного эстиматора в shap (python)
