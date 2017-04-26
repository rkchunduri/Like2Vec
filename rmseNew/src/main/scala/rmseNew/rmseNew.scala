package rmseNew
import java.io.File

import org.apache.spark._
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd._
import org.apache.spark.SparkContext._
import org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER


import scala.io.Source


case class Prediction(Item:Long,actualResult:Int,predictedResult:Int)

object RmseNew {
 

  def getConfMap(dir: String)(implicit sc: SparkContext): scala.collection.Map[String, String] = {
    sc.textFile(dir).map { line =>
      val Array(key, value) = line.split(";")
      (key, value)
    }.collectAsMap()
  }

  def getTrainOrTest(fileName: String)(implicit sc: SparkContext): RDD[(Int, Int, Double)] = {
        sc.textFile(fileName).map{
      line =>
    try {
          val Array(user, movie, rating) = line.split(",")
        (user.toInt, movie.toInt, rating.toDouble)
      }
      catch {
      case e: Exception => (0, 0, 0)
    }
   }
  }
  

  def getParamMap(hyperParamDirs: List[String])(implicit sc: SparkContext): Map[String, Array[String]] = {
  
    hyperParamDirs.flatMap { fileName =>
      sc.textFile(fileName).map(i => i.replace("(", "").replace(")", "").split(",")).map(i => (i(0), i.drop(1))).collect()
    }.toMap
  }

  def getEmbedHyperParam(hyperParamMap: Map[String, Array[String]])(sampleFile: String)(implicit sc: SparkContext): (Array[String], RDD[(Int, Array[Double])]) = {
      val embeddings = sc.textFile(sampleFile).map { line =>
       val fields = line.split(" ")    
      val movie_id = (fields.head.toInt)
      val vector = fields.tail.map(_.toDouble).toArray
      (movie_id, vector)
  }
    val str = "0"
    (hyperParamMap(str), embeddings)

  }

  def rmseCalculate(getEmbeddings: String => (Array[String], RDD[(Int, Array[Double])]), numMovies: Int,
                    trainData: List[RDD[(Int, List[(Int, Double)])]], testData: RDD[(Int, List[(Int, Double)])],
                    typeAvg: Int, outputDir: String)(embedFile: String)(implicit sc: SparkContext): Unit = {
    
    def cosineSimilarity(arr1: Array[Double], arr2: Array[Double]): Double = {
     
       
      def dotProduct(x: Array[Double], y: Array[Double]): Double = {
       
        x.zipWithIndex.foldLeft(0.0)((acc, tup) => acc + tup._1 * y(tup._2))
      }
      def norm(x: Array[Double]): Double = {
       
        math.sqrt(dotProduct(x, x))
      }

      val numerator = dotProduct(arr1, arr2)
      val denom1 = norm(arr1)
      val denom2 = norm(arr2)
      val denominator = denom1 * denom2

      (numerator / denominator)
    }

    def weightedAverage(topItems: List[(Double, Double)]): Double = {
    
      val (num, denom) = topItems.foldLeft((0.0, 0.0)) { (acc, entry) =>
        val expWeight = math.exp(entry._1)
        val sumWeightedRatings = acc._1 + (expWeight * entry._2)
        val sumOfDistances = acc._2 + expWeight
        (sumWeightedRatings, sumOfDistances)
      }
      num / denom
    }

    def naiveAverage(topItems: List[(Double, Double)]): Double = {
     
      topItems.map(_._2).sum / numMovies
    }

    val (Array(embedingSize, walkLength, numWalks), embeddingRDD) = getEmbeddings(embedFile)
    val embeddings = sc.broadcast(embeddingRDD.collectAsMap())

//    def calcErr(cntr: Integer): (Double, Int) = {
//      
//      val embkeys = embeddingRDD.map(_._1).collect()    
//      val toBroadcast = trainData(cntr)  .collectAsMap()
//      val trainUsers = toBroadcast.keys.toArray
//      val trainBroadcast = sc.broadcast(toBroadcast) 
//
//      val err_sqr =
//        testData
//          .filter {
//            case (x: Int, y: List[(Int, Double)]) =>
//              trainUsers.contains(x)        }          
//          .map {
//            case (user, testList) =>
//              val trainList = trainBroadcast.value(user) //.filter(_._1 != testMovie)
//
//              val trainEmbed = trainList.map {
//
//                case (movie, rate) =>
//                 
//                  (movie, embeddings.value(movie), rate)
//               
//              }
//
//              val testEmbed = testList.map {
//                case (testMovie, testRate) =>
//                 
//                  (testMovie, embeddings.value(testMovie), testRate)
//               
//              }
//              testEmbed.map {
//                case (testMovie, testEmbedding, testRate) =>
//                  val trainFilter = trainList.filter(_._1 != testMovie)
//                  val dist = trainEmbed.map {
//                    case (trainMovieId, trainEmbed, trainRate) =>
//                      (cosineSimilarity(testEmbedding, trainEmbed), trainRate)
//                  }
//                  val topItems = dist.sortBy(_._1)(Ordering[Double].reverse).take(numMovies)
//                  val predictedRate = if (typeAvg == 0) weightedAverage(topItems) else naiveAverage(topItems)
//
//                 
//                  val err = testRate - predictedRate
//               
//                  (math.pow(err, 2.0), 1)
//              }.reduce((acc, elem) => (acc._1 + elem._1, acc._2 + elem._2))
//
//          }
//
//      //      println(err_sqr)
//      val partialResult = err_sqr.reduce((acc, elem) => (acc._1 + elem._1, acc._2 + elem._2))
//      //      println(partialResult)
//
//      trainBroadcast.unpersist()
//      partialResult
//    }

    def predict(cntr: Integer): Unit = {
     
      val embkeys = embeddingRDD.map(_._1).collect()

      // broadcast training set as a hashmap for efficient calculation at the workers
      val toBroadcast = trainData(cntr).collectAsMap()
      val trainUsers = toBroadcast.keys.toArray
      val trainBroadcast = sc.broadcast(toBroadcast)

      // make prediction for each of the rows in the test set

      val prediction =
        testData
          .filter {
            case (x: Int, y: List[(Int, Double)]) =>
              trainUsers.contains(x)
          }.map {
            case (user, testList) =>
              val trainList = trainBroadcast.value(user) //.filter(_._1 != testMovie)
              val trainEmbed = trainList.map {
                case (movie, rate) =>
                  (movie, embeddings.value(movie), rate)
              }

              val testEmbed = testList.map {
                case (testMovie, testRate) =>
                  (testMovie, embeddings.value(testMovie), testRate)
              }

              val predictions = testEmbed.map {
                
              
                case (testMovie, testEmbedding, testRate) =>
                  val trainFilter = trainList.filter(_._1 != testMovie)
                  val dist = trainEmbed.map {
                    case (trainMovieId, trainEmbed, trainRate) =>
                      (cosineSimilarity(testEmbedding, trainEmbed), trainRate)
                  }
                  val topItems = dist.sortBy(_._1)(Ordering[Double].reverse).take(numMovies)
                  val predictedRate = if (typeAvg == 0) weightedAverage(topItems) else naiveAverage(topItems)
                  
                   val errorCalc =math.pow((testRate-predictedRate),2)           
                    (testMovie, testRate, predictedRate,errorCalc)
               }
              
             val denom= predictions.map(_._4).size
             val numerator =predictions.map(_._4).reduce((acc, elem) => (acc + elem))
           
             val rmseValue =Math.sqrt(numerator/denom)
          
              
         
          
              (user,predictions,rmseValue)
              
        
              
              
              
              
              

          } //.reduceByKey(_++_)
          
          
          
             
                  
                 

      trainBroadcast.unpersist()
        println("Writing Predictions")
        
      prediction.map(line=>line._1+";"+line._3+","+line._2).coalesce(1).saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/rmseNew/rmseNewpredictions9") 
  
               
      

    }
    
      (0 to 0).map(i => predict(i))

    //val (rmseNum, rmseDenom) = (0 to 0).map(i => calcErr(i)).reduce((a, b) => (a._1 + b._1, a._2 + b._2))
  
    //    val (rmseNum, rmseDenom) = (0 to 24).map(i => calcErr(i)).reduce((a,b) => (a._1+b._1, a._2+b._2))
    //embeddings.unpersist()
    //val listOutput = List(embedingSize, walkLength, numWalks, math.sqrt(rmseNum / rmseDenom))
    //sc.parallelize(listOutput).coalesce(1).saveAsTextFile(outputDir + s"${embedingSize}_${walkLength}_${numWalks}")

  }

  def main(args: Array[String]) {
  
     val s3Bkt = args(0)

  

    //initialize spark configuration
    val conf = new SparkConf()
      .setAppName("RmseNew")
      .setMaster("local[*]")
  
    implicit val sc = new SparkContext(conf)
    val confMap = getConfMap(s3Bkt + "confRmse.txt")
   
    val embs = sc.textFile("/Users/raghavendrakumar/rmsetest/outputavg/100-10-10.embeddings0")
    val embsItem = embs.map { line =>
      val perone = line.split(" ")
      val embedding = perone.head.toInt
      embedding
    }.distinct.collect()

    val trainDir = s3Bkt + confMap("trainDir")
    val tD1 = getTrainOrTest(trainDir)
    val tdUser = tD1.map(_._1).distinct().collect()
    val tdMovie = tD1.map(_._2).distinct().collect()

    val tDfiltered = tD1.filter { case (i, j, k) => embsItem.contains(j) }

    val tD = tDfiltered.map { case (i, j, k) => (i, List((j, k))) }.reduceByKey(_ ++ _)

    //    val trainData = (1 to 25).map(i => tD.filter{case (k,v) => (k>= ((i-1)*20000)) && (k<(i*20000))}).toList
    val trainData = (1 to 25).map(i => tD.filter { case (k, v) => (k >= ((i - 1) * 20000)) && (k < (i * 20000)) }).toList
    //    val trainData = tD

    /* convert test data to a RDD
          key: user id
          value: list of all movies user has reviewed, List[(movie id, rating)]
     */
    val testDir = s3Bkt + confMap("testDir")
    val testData = getTrainOrTest(testDir).filter { case (i, j, k) => tdUser.contains(i) && tdMovie.contains(j) && embsItem.contains(j) } // filtering test set on training user/movies
      .map { case (i, j, k) => (i, List((j, k))) }.reduceByKey(_ ++ _)

    /* get hyperparameter map
          key: index of L2V run
          value: Array[(size of embeddings, length of each random walk, number of random walks per node)]
     */
    val hyperParamDir = s3Bkt + confMap("hyperParamDir")
    val hyperParamDirNum = confMap("hyperParamDirNum").toInt
    val hyperParamFiles = (0 to hyperParamDirNum).map { i =>
      val strNum = i.toString
      val zeros = "0" * (5 - strNum.length)
      val finalNum = zeros + strNum
      hyperParamDir + s"part-${finalNum}"
    }.toList
    val hyperParamMap = getParamMap(hyperParamFiles)
    
    
    

    // get the list of all embedding files
    val embeddingDir = s3Bkt + confMap("embeddingDir")

    val embeddingDirNum = confMap("embeddingDirNum").toInt
   
    val embeddingFiles = (0 to embeddingDirNum).map { i =>
      embeddingDir + s"100-10-10.embeddings${i}"
    }.toList

    

    val getEmbed = getEmbedHyperParam(hyperParamMap)_

    // initialize function that calculates RMSE with values that are constant for all sets of embeddings
    val numMovies = confMap("numMovies").toInt
    val typeAvg = confMap("typeAvg").toInt
    val outputDir = s3Bkt + confMap("outputDir")
    val getRmse = rmseCalculate(getEmbed, numMovies, trainData, testData, typeAvg, outputDir)_ // assign function to variable

    // iterate through each embedding file and get its accompanying hyperparameters and rmse
    val rmseOutput = embeddingFiles.map(getRmse)

  }
}