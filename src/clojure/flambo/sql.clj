;;
;; SparkSQL & DataFrame wrapper
;;
(ns flambo.sql
  (:refer-clojure :exclude [load])
  (:require [flambo.api :as f :refer [defsparkfn]])
  (:import [org.apache.spark.api.java JavaSparkContext]
           [org.apache.spark.sql SQLContext Row Dataset]))

;; ## SQLContext
;;
(defn ^SQLContext sql-context
  "Build a SQLContext from a JavaSparkContext"
  [^JavaSparkContext spark-context]
  (SQLContext. spark-context))

(defn ^JavaSparkContext spark-context
  "Get reference to the SparkContext out of a SQLContext"
  [^SQLContext sql-context]
  (JavaSparkContext/fromSparkContext (.sparkContext sql-context)))

(defn sql
  "Execute a query. The dialect that is used for SQL parsing can be configured with 'spark.sql.dialect'."
  [sql-context query]
  (.sql sql-context query))

(defmacro with-sql-context
  [context-sym conf & body]
  `(let [~'x (f/spark-context ~conf)
         ~context-sym (sql-context ~'x)]
     (try
       ~@body
       (finally (.stop ~'x)))))

(defn parquet-file
  "Loads a Parquet file, returning the result as a Dataset."
  [sql-context path]
  (.parquetFile sql-context path))

(defn json-file
  "Loads a JSON file (one object per line), returning the result as a DataFrame."
  [sql-context path]
  (.jsonFile sql-context path))

;; Since 1.3 the SparkSQL data sources API is recommended for load & save operations.
(defn load
  "Returns the dataset stored at path as a Dataset."
  ([sql-context path]                   ; data source type configured by spark.sql.sources.default
   (.load sql-context path))
  ([sql-context path source-type]       ; specify data source type
   (.load sql-context path source-type)))

(defn read-csv
  "Reads a file in table format and creates a data frame from it, with cases corresponding to
  lines and variables to fields in the file. A clone of R's read.csv."
  [sql-context path &{:keys [header separator quote]
                      :or   {header false separator "," quote "'"}}]
  (let [options (new java.util.HashMap)]
    (.put options "path" path)
    (.put options "header" (if header "true" "false"))
    (.put options "separator" separator)
    (.put options "quote" quote)
    (.load sql-context "com.databricks.spark.csv" options)))

(defn register-data-frame-as-table
  "Registers the given DataFrame as a temporary table in the
  catalog. Temporary tables exist only during the lifetime of this
  instance of SQLContex."
  [sql-context df table-name]
  (.registerDataFrameAsTable sql-context df table-name))

(defn cache-table
  "Caches the specified table in memory."
  [sql-context table-name]
  (.cacheTable sql-context table-name))

(defn json-rdd
  "Load an RDD of JSON strings (one object per line), inferring the schema, and returning a DataFrame"
  [sql-context json-rdd]
  (.jsonRDD sql-context json-rdd))

(defn uncache-table
  "Removes the specified table from the in-memory cache."
  [sql-context table-name]
  (.uncacheTable sql-context table-name))

(defn clear-cache
  "Remove all tables from cache"
  [sql-context]
  (.clearCache sql-context))

(defn is-cached?
  "Is the given table cached"
  [sql-context table-name]
  (.isCached sql-context table-name))

(defn ^Dataset table
  "Return a table as a DataFrame"
  [sql-context table-name]
  (.table sql-context table-name))

(defn table-names
  "Return a seq of strings of table names, optionally within a specific database"
  ([sql-context]
   (seq (.tableNames sql-context)))
  ([sql-context database-name]
   (seq (.tableNames sql-context database-name))))

;; DataFrame
(defn register-temp-table
  "Registers this dataframe as a temporary table using the given name."
  [df table-name]
  (.registerTempTable df table-name))

(defn columns
  "Returns all column names as a sequence."
  [df]
  (seq (.columns df)))

(def print-schema (memfn printSchema))

;; Row
(defsparkfn row->vec [^Row row]
  (let [n (.length row)]
    (loop [i 0 v (transient [])]
      (if (< i n)
        (recur (inc i) (conj! v (.get row i)))
        (persistent! v)))))
