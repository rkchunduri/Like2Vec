package lshinput
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.Vectors
import com.github.karlhigley.spark.neighbors.ANN

import org.apache.spark.SparkConf
object LSHInput {

  def main(args: Array[String]) {

    val sc = new SparkContext("local","LSHEmbeddings");
    val inputRDD = sc.textFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/part00000_threshold.embeddings")
    val header =inputRDD.first()
    val embeedings_data = inputRDD.filter(row => row != header)  
   // inputRDD.collect().foreach(println)
    val points =getEmbeddingsinLSHFormat(embeedings_data)
     val ann =
      new ANN(100, "cosine")
        .setTables(1)
        .setSignatureLength(16)

    val model1 = ann.train(points)
    
    
   val nn=model1.neighbors(10).map{case(user,neighborsList) =>(user,neighborsList.map(_._2).reduce((acc, elem) => (acc + elem)),neighborsList.toList)}
  
   nn.saveAsTextFile("LSHOPforRMSE1") 
   
   
 }

  def getEmbeddingsinLSHFormat(input: RDD[String]): RDD[(Long, SparseVector)] = {
    input.map {
      line =>
        val fields = line.split(" ")
        val tail = fields.tail.map(x => x.toDouble)
        val sparseVectorEmbeddings = Vectors.dense(tail).toSparse

    // println(sparseVectorEmbeddings)
        (fields.head.toLong, sparseVectorEmbeddings)
    }

  }

}