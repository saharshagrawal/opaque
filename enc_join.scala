import edu.berkeley.cs.rise.opaque.implicits._
// import org.apache.spark.sql.SparkSession
// import org.apache.spark.SparkContext
  

// object Join {
// 	def main(args: Array[String]): Unit = {
// 		val spark = SparkSession.builder.
// 		    master("local[*]").
// 		    appName("Console").
// 		    config("spark.app.id", "Console").   
// 		    getOrCreate()
edu.berkeley.cs.rise.opaque.Utils.initSQLContext(spark.sqlContext)

val df1 = spark.read.json("person.json")
val df1_repart = df1.repartition(5)
// println(s"df1 num partitions: ${df1_repart.rdd.getNumPartitions}")
df1_repart.rdd.getNumPartitions


val df1_repart_enc = df1_repart.encrypted

val df2 = spark.read.json("idlist.json")
val df2_repart = df2.repartition(3)
// println(s"df2 num partitions: ${df2_repart.rdd.getNumPartitions}")

df2_repart.rdd.getNumPartitions
val df2_repart_enc = df2_repart.encrypted

df1_repart_enc.repartition(5)

val enc_join = df1_repart_enc.join(df2_repart_enc, Seq("PersonID"))
enc_join.show
// 	}
// }