package map;
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.rdd.RDD
import org.apache.spark.SparkConf
import org.apache.spark.mllib.evaluation.RankingMetrics



object MeanAveragePrecision {

  def main(args: Array[String]) {

    val sc = new SparkContext("local","MeanAveragePrecision");
   
    
     val actualRatings = sc.textFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/predictions/part-00000").map { line =>
   
     val data =line.replace("(", "").replace(")", "").split(",")
     val user= data(0).toLong
     val movie = data(1).toLong
     val actualRating= data(2).toDouble
     val predictedRating=data(3).toDouble
    (user,movie,actualRating)
    }.sortBy(f=>f._3,ascending=false)
    
     
    val predictedRatings = sc.textFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/predictions/part-00000").map{
      line => 
     val data =line.replace("(", "").replace(")", "").split(",")
     val user= data(0).toLong
     val movie = data(1).toLong
    val actualRating= data(2).toDouble
     val predictedRating=data(3).toDouble
    (user,movie,predictedRating)
    }.sortBy(f=>f._1, ascending=true,1)
     
  val predictedUser = predictedRatings.map{case(user,movie,rating)=> (user,movie)}.groupByKey().map( f=>f._1)
   
   val actualMovies =actualRatings.filter(f=>f._3 > 3.0).map{case(user,movie,rating)=> (user,movie)}.groupByKey().collectAsMap()
   
   val actualMoviesKeySet =actualMovies.keySet
   //filter(f=>f._3 > 3.0)
   val predictedMovies =predictedRatings.map{case(user,movie,rating)=> (user,(movie,rating))}.groupByKey().collectAsMap()
    
    val averagePrecision =predictedUser.filter(f=>actualMovies.contains(f)).map{ case(user) => 
     
        val actualMoviesforAP =actualMovies.apply(user).toList
 
   
     val predictedMoviesforAP = predictedMovies.apply(user).toList.sortBy(f=>f._2).reverse.map{case(movie,rating) => movie }.take(10)
 
  
   
   (avgPrecisionK(actualMoviesforAP,predictedMoviesforAP,10))
 
}
   
   
 
  averagePrecision.saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/averageprecision")
      
    val mapK =   (averagePrecision.reduce(_+_) /averagePrecision.count())
    
    println(mapK+"Mean Average Precision at K")




 
 
 //map{case(movie,rating) => (rating,movie)}.top(5).toSeq).map{case(rating,movie)=>movie}.collect()
   
    //avgPrecisionK(actualMoviesforAP,predictedMoviesforAP,1)
  
     //.saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/averageprecision")
      
    
   // }
    
    
     
     
     
     
  
  
 

  // val sortedPredictedRatings =   predictedRatings.sortBy(f=>f._1, ascending=true,1).saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/sortedPredictedRatings")
   //val sortedActualRatings = actualRatings.sortBy(f=>f._1, ascending=true,1).saveAsTextFile("/Users/raghavendrakumar/L2V_Sandbox-master/llr/sortedActualRatings")
   
   }
  
  def avgPrecisionK(actual: List[Long], predicted: List[Long], k: Int):
   Double = {
    // val predK = predicted.take(k)
       actual.foreach(println)
  
      predicted.foreach(println)
       
     var score = 0.0
     var numHits = 0.0
     for ((p, i) <- predicted.zipWithIndex) {     
       if (actual.contains(p)) {
         numHits = numHits+ 1.0        
         score = score + numHits / (i.toDouble + 1.0)      
    } }
if (actual.isEmpty) {
  1.0

     } else {     
       
       score / scala.math.min(actual.size, k).toDouble
             
} }
 
 
   
 }
