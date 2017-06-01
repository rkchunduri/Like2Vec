package llr

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import java.util.Random
import scala.collection.mutable
import org.apache.spark.serializer.KryoRegistrator
import com.esotericsoftware.kryo.Kryo
import org.apache.spark.SparkConf

case class Interaction(val user: String, val item: String)

object LLR {
 val hdfs ="hdfs://ec2-52-88-52-106.us-west-2.compute.amazonaws.com"
  val inputFile =hdfs+"/user/hadoop/ratings_train"
  def main(args: Array[String]) {
    userSimilarites(
      "master",
       //args(0),
      inputFile,
       ",",
       2,
       100,
       500,
       12345,
       0.3)
  }
   
  def userSimilarites(
    master:String,
    interactionsFile:String,
    separator:String,
    numSlices:Int,
    maxSimilarItemsperItem:Int,
    maxInteractionsPerUserOrItem:Int,
    seed:Int,
    threshold:Double){
  // System.setProperty("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
   // System.setProperty("spark.kryo.registrator", classOf[CoocRegistrator].getName)
  //System.setProperty("spark.kryo.referenceTracking", "false")
 // System.setProperty("spark.kryoserializer.buffer.mb", "8")
    System.setProperty("spark.locality.wait", "10000")
 // val sc = new SparkContext("local","CooccurrenceAnalysis");

val sc = new SparkContext(new SparkConf().setAppName("CooccurrenceAnalysis"));

    //Reading Data from input file,RDD
    val rawInteractions = sc.textFile(interactionsFile)
    val header =rawInteractions.first()
    val rawInteractions_data = rawInteractions.filter(row => row != header)       
    val  rawInteractions_set =rawInteractions_data.map { line =>
    val fields = line.split(separator)
    Interaction(fields(0), fields(1))
  }
  val interactions =
    downSample(
      sc, 
      rawInteractions_set, 
      maxInteractionsPerUserOrItem, 
      seed)
      interactions.cache()
      val numInteractions = interactions.count()
      val numInteractionsPerItem =
        countsToDict(interactions.map(interaction => (interaction.user, 1)).
        reduceByKey(_ + _))
      sc.broadcast(numInteractionsPerItem)
      val numItems =
        countsToDict(interactions.map(interaction => (interaction.item, 1)).
        reduceByKey(_ + _))
      val cooccurrences = interactions.groupBy(_.item).
        flatMap({ case (user, history) => {
          for (interactionA <- history; interactionB <- history)
          yield { ((interactionA.user, interactionB.user), 1l)
          }
        } 
      }).reduceByKey(_ + _)
    
      val similarities = cooccurrences.map({ case ((userA, userB), count) =>{
        val interactionsWithAandB = count
        val interactionsWithAnotB = 
          numInteractionsPerItem(userA) - interactionsWithAandB
        val interactionsWithBnotA = 
          numInteractionsPerItem(userB) - interactionsWithAandB
        val interactionsWithNeitherAnorB =
          (numItems.size) - numInteractionsPerItem(userA) - 
          numInteractionsPerItem(userB) + interactionsWithAandB
        val logLikelihood = 
          LogLikelihood.logLikelihoodRatio(
            interactionsWithAandB, 
            interactionsWithAnotB,
            interactionsWithBnotA, 
            interactionsWithNeitherAnorB)
        val logLikelihoodSimilarity = 1.0 - 1.0 / (1.0 + logLikelihood)
    
        
   ((userA, userB), logLikelihoodSimilarity)
      
   
   
  //loglikelihoodData.toString().replaceAll("\\(","").replaceAll("\\)","").replaceAll(",", " ")
      }
  })
    
  // similarities.repartition(1).saveAsTextFile(hdfs+"/user/hadoop/LlrTrainSet");
    
 similarities.filter(f=>f._2.toDouble >threshold).repartition(1).saveAsTextFile(hdfs+"/user/hadoop/LLROP");
  sc.stop()
}

  def downSample(
    sc:SparkContext,
    interactions: RDD[Interaction], 
    maxInteractionsPerUserOrItem: Int, 
    seed: Int) = {
    val numInteractionsPerUser = 
      countsToDict(interactions.map(interaction => (interaction.user, 1)).
      reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerUser)
    val numInteractionsPerItem =
      countsToDict(interactions.map(interaction => (interaction.item, 1)).
           reduceByKey(_ + _))
    sc.broadcast(numInteractionsPerItem)
    def hash(x: Int): Int = {
      val r = x ^ (x >>> 20) ^ (x >>> 12)
      r ^ (r >>> 7) ^ (r >>> 4)
    }
/* apply the filtering on a per-partition basis to ensure repeatability in case of failures by
       incorporating the partition index into the random seed */
    interactions.mapPartitionsWithIndex({ case (index, interactions) => {
      val random = new Random(hash(seed ^ index))
      interactions.filter({ interaction => {
        val perUserSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerUser(interaction.user)) / 
          numInteractionsPerUser(interaction.user)
        val perItemSampleRate = math.min(
          maxInteractionsPerUserOrItem, numInteractionsPerItem(interaction.item)) / 
          numInteractionsPerItem(interaction.item)
        random.nextDouble() <= math.min(perUserSampleRate, perItemSampleRate)
      }
    })
  }
})
}
  
  //counts the number of Interactions per user and item.

  def countsToDict(tuples: RDD[(String, Int)]) = {
    tuples.collect().foldLeft(Map[String, Int]()) {
      case (table, (item, count)) => table + (item -> count) 
    }
  }

}

object LogLikelihood {

  def logLikelihoodRatio(k11: Long, k12: Long, k21: Long, k22: Long) = {
    val rowEntropy: Double = entropy(k11 + k12, k21 + k22)
    val columnEntropy: Double = entropy(k11 + k21, k12 + k22)
    val matrixEntropy: Double = entropy(k11, k12, k21, k22)
    if (rowEntropy + columnEntropy < matrixEntropy) {
      0.0
    } else {
      2.0 * (rowEntropy + columnEntropy - matrixEntropy)
    }
  }

  private def xLogX(x: Long): Double = {
    if (x == 0) {
      0.0
    } else {
      x * math.log(x)
     
    }
  }

  private def entropy(a: Long, b: Long): Double = { xLogX(a + b) - xLogX(a) - xLogX(b) }

  private def entropy(elements: Long*): Double = {
    var sum: Long = 0
    var result: Double = 0.0
    for (element <- elements) {
      result += xLogX(element)
      sum += element
    }
    xLogX(sum) - result
  }
}