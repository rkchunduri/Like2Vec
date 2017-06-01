package lshinput
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.linalg.SparseVector
import org.apache.spark.mllib.linalg.Vectors
import com.github.karlhigley.spark.neighbors.ANN

import org.apache.spark.SparkConf
object LSHInputAndRMSE {

  def main(args: Array[String]) {

    implicit val sc = new SparkContext("local", "LSHEmbeddings");

    val trainData = getTrainData("/Users/raghavendrakumar/rmsetest/ratings_train")

    val neighbors = getNeighbors("/Users/raghavendrakumar/L2V_Sandbox-master/llr/part00000_threshold.embeddings")

    val predictions = predictionCalculation(trainData, neighbors)
    
    predictions._1.saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/predictions")
    
    sc.parallelize(Seq(predictions._2)).coalesce(1).saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/rmseValue1")



    //nn.saveAsTextFile("LSHOPforRMSE1") 

  }

  def predictionCalculation(trainData: RDD[(Int, List[(Long, Double)])], neighbors: RDD[(Long, List[(Long, Double)])])(implicit sc: SparkContext) = {

    val trainDataAsMap = trainData.collectAsMap()

    val neighborDataAsMap = neighbors.collectAsMap()

    val neighborUserKeys = neighbors.collectAsMap().keySet
    
    val trainDataMovieKeys =trainDataAsMap.keySet

    val predictions = sc.textFile("/Users/raghavendrakumar/rmsetest/ratings_test").filter(!_.isEmpty()).map { line =>
      val test = line.split(",")

      val testUser = test(0).toLong
      val testMovie = test(1).toInt
      val testRating = test(2).toDouble
    (testUser,testMovie,testRating)
    }
    
    val neighborPredictions =predictions.filter(f => neighborUserKeys.contains(f._1) && trainDataMovieKeys.contains(f._2) ).map{case(testUser,testMovie,testRating) =>
     
      val trainuser = trainDataAsMap.apply(testMovie).toMap

      val neighborUser = neighborDataAsMap.apply(testUser).toMap

      val userweight = trainuser.keySet.intersect(neighborUser.keySet).map { f => (f, trainuser.get(f).getOrElse(0).asInstanceOf[Double], neighborUser.get(f).getOrElse(0).asInstanceOf[Double]) }.toList

      val totalDistance = userweight.map(_._3).sum

      val predictedRate = userweight.map {
        case (user, rating, distance) => ((distance / totalDistance) * rating)

      }.sum

      val errorCalc = math.pow((testRating - predictedRate), 2)   
     (testUser, testMovie, testRating, predictedRate, errorCalc)
    
     
    }
    val denom= neighborPredictions.map(_._5).count()
    val numerator = neighborPredictions.map(_._5).reduce((acc, elem) => (acc + elem))

    val rmseValue = Math.sqrt(numerator / denom)


    (neighborPredictions, rmseValue)

    

  
  }

  def getNeighbors(file: String)(implicit sc: SparkContext): RDD[(Long, List[(Long, Double)])] = {

    val inputRDD = sc.textFile(file)
    val header = inputRDD.first()
    val embeedings_data = inputRDD.filter(row => row != header)
    // inputRDD.collect().foreach(println)
    val points = getEmbeddingsinLSHFormat(embeedings_data)
    val ann =
      new ANN(100, "cosine")
        .setTables(1)
        .setSignatureLength(16)

    val model1 = ann.train(points)

    val nn = model1.neighbors(10).map { case (user, neighborsList) => (user, neighborsList.toList) }
    //, neighborsList.map(_._2).reduce((acc, elem) => (acc + elem))
    nn

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

  def getTrainData(fileName: String)(implicit sc: SparkContext): RDD[(Int, List[(Long, Double)])] = {

    val trainData = sc.textFile(fileName).map { line =>
      val train = line.split(",")
      val user = train(0)
      val item = train(1)
      val rating = train(2)

      (item.toInt, List((user.toLong, rating.toDouble)))

    }

    val formatted = trainData.reduceByKey(_ ++ _)
    formatted
  }

}