//package avgEmbed

package avgEmbed


import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark._
import org.apache.spark.rdd.RDD
import java.io.File
import org.apache.spark.rdd.RDD.rddToPairRDDFunctions

object avgEmbed{

  def getUnionRDD(dir: String)(implicit sc: SparkContext) = {

    def getListOfFiles(dir: String):List[String] = {

      val d = new File(dir)
      d.listFiles.filter(_.isFile).map(_.toString).toList
    }

    def getParamMap(z:List[String]): Map[String, Array[String]] = {
      
      

      z.filter(i => i.contains("art")&& !i.contains("crc")).
        flatMap(k => scala.io.Source.fromFile(k).getLines.toArray.map(i => i.replace("(","").
          replace(")","").split(",")).map(i=> (i(0),i.drop(1)))).toMap
          
          
    }

   
    def getRunNum(str: String) = str.split("/").last.split("_").last.split(".txt")(0)
   

    def go(hyperMap:scala.collection.immutable.Map[String,Array[String]])(file: List[String],
                                                                          acc: RDD[((Int,String,String), Array[Double])]):
                                                                          RDD[((Int,String,String),Array[Double])] = {

      if(file.length==0) acc
      else {
        val hyperParam = hyperMap(getRunNum(file.head))
        val acc1 = acc ++ sc.textFile(file.head).map{i =>
                    val lines = i.split(",")
                    val (movie,embedVec) = (lines.head.toInt,lines.tail.map(_.toDouble))
                    ((movie,hyperParam(0),hyperParam(2)),embedVec)
                  }
        go(hyperMap)(file.tail,acc1)
      }

    }

    val hyperparamFiles = getParamMap(getListOfFiles(s"/users/raghavendrakumar/hyperParam/hyperparam${dir}16.txt/"))
    val embed = getListOfFiles(s"/users/raghavendrakumar/output/output${dir}/")
    val iter = go(hyperparamFiles)_
    
    println(embed.head);
    val hyperParam = hyperparamFiles(getRunNum(embed.head))
    (iter(embed.tail,sc.textFile(embed.head).map{i =>
      val lines = i.split(",")
      val (movie,embedVec) = (lines.head.toInt,lines.tail.map(_.toDouble))
      ((movie,hyperParam(0),hyperParam(2)),embedVec)
    }),hyperparamFiles)


  }

  def averageRDD(rddArray: Array[RDD[((Int,String,String),Array[Double])]]) = {
    def go(iterRdd: Array[RDD[((Int,String,String),Array[Double])]], acc: RDD[((Int,String,String),Array[Double])],cnt:Double):
      RDD[((Int,String,String),Array[Double])] = {
      if (iterRdd.length==0) acc.map{case ((movieId,es,nw),embed) => ((movieId,es,nw),embed.map(_/cnt))}
      else {
        val acc1 = acc.join(iterRdd.head).map{case ((movieId,es,nw),(embedAcc,embed)) =>
          ((movieId,es,nw),(embedAcc,embed).zipped.map(_+_))}
        go(iterRdd.tail, acc1,cnt+1)}
    }
    go(rddArray.tail, rddArray.head,1)
  }


  def main(args: Array[String]): Unit ={
    val conf = new SparkConf()
                .setMaster("local[*]")
                .setAppName("avgEmbed")
    implicit val sc = new SparkContext(conf)
    val (outputDir,inputDirs) = (args.head,args.tail)
    val unionOutput = inputDirs.map(getUnionRDD)
  println(outputDir)
    var cntr = -1
    val (rddArray,uniqueHyperParam) = (unionOutput.map(_._1),unionOutput.map(_._2).reduce(_++_).values.map{j => ((j(0),j(2)))}.toSet)
    val completeHyperParam = uniqueHyperParam.map{case (i,j) => cntr+=1; (cntr,(i,j))}.toMap
    sc.parallelize(completeHyperParam.map{case (i,(j,k)) => (i,(j.toInt,678,k.toInt))}.toSeq).saveAsTextFile(outputDir+"/hyperParamAvg")
    val averageAllRdd = averageRDD(rddArray)
    completeHyperParam.map{ case(id,(em,nw)) =>
                            averageAllRdd.filter{case ((mid,ema,nwa),embedArr) => ema==em && nwa==nw}
                              .map{case ((mid,ema,nwa),embedArr) => (mid,embedArr)}.coalesce(1).saveAsTextFile(outputDir+s"/llr_output_test_avg_${id}")}

  }
}