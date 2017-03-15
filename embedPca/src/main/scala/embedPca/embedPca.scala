package embedPca

import java.io.File
import org.apache.spark.mllib.linalg.distributed.RowMatrix
import org.apache.spark.mllib.linalg.{Matrix, Vectors}
import org.apache.spark.mllib.linalg.Vector
import org.apache.spark.mllib.linalg.DenseVector
import org.apache.spark.rdd._
import org.apache.spark.{SparkContext, SparkConf}



object embedPca{
  /*
  generate covariance matrix for each set of embeddings that will eventually be used to calculate the explained variance
  for each dimension
  at the time of writing this Spark's PCA function did not allow you to get the explained variance for each dimension
  so I had to put together this somewhat hacky solution
   */
  def pcaAll( outputDir: String, hyperParamMap: Map[String, Array[String]])(inputDir: String)(implicit sc: SparkContext): Unit ={
    /*
    calculate covariance matrix for item embeddings and then saves the result to a text file

    Parameters
    ----------
    outputDir: String; location where the covariance matrix will be written to
    hyperParamMap: Map[String, Array[String]];

    inputDir: String;

    sc: SparkContext;

    Return
    ------
    Unit; the covariance matrix is saved to a textfile

     */
    def matrixToRDD(m: Matrix): RDD[Vector] = {
      /*
      This function comes from http://stackoverflow.com/questions/30169841/convert-matrix-to-rowmatrix-in-apache-spark-using-scala
      It converts a spark matrix to a RDD

      Parameters
      ----------
      m: Matrix; covariance matrix

      Return
      ------
      RDD[Vector]; covariance matrix in RDD forma

       */
      val columns = m.toArray.grouped(m.numRows)
      val rows = columns.toSeq.transpose // Skip this if you want a column-major RDD.
      val vectors = rows.map(row => new DenseVector(row.toArray))
      sc.parallelize(vectors)
    }

    val embed = sc.textFile(inputDir).map{i =>
                                              val embedding = i.replace("List","").replace(")","").replace("(","").split(",")
                                                                  .tail.map(_.toDouble)
                                              Vectors.dense(embedding)}
    val dim = embed.take(1).length
    val rowMatrix = new RowMatrix(embed)
    val covMat = rowMatrix.computeCovariance()
    val hp = hyperParamMap(inputDir.split("/").last.split("_").last.split(".txt")(0))
    matrixToRDD(covMat).coalesce(1).saveAsTextFile(outputDir+s"_${hp(0)}_${hp(1)}_${hp(2)}_covariance")
  }

  def getListOfFiles(dir: String):List[String] = {
    /*
    get the list of files from a folder

    Parameters
    ----------
    dir: String; location of folder containing the files

    Return
    ------
    List[String]; list containing full location of the files in the given folder

     */
    val d = new File(dir)
    d.listFiles.filter(_.isFile).map(_.toString).toList
  }

  def getParamMap(z:List[String]): Map[String, Array[String]] = {
    /*
    convert list of files into a map containing the hyperparameter values used to generate each set of embeddings

    Parameters
    ----------
    z: List[String]; location of all the files

    Return
    ------
    Map[String, Array[String]]; map where key is the embedding id and value are the hyperparameters used for that
                                set of embeddings

     */
    z.filter(i => i.contains("art")&& !i.contains("crc")).
      flatMap(k => scala.io.Source.fromFile(k).getLines.toArray.map(i => i.replace("(","").
        replace(")","").split(",")).map(i=> (i(0),i.drop(1)))).toMap
  }

  def main(args: Array[String]) {
    /*

    Parameter
    ---------
    args: Array[String]; first element is the location of where you want the results written to, the last value is the
                         location of the folder holding all the hyperparameter files, all files inbetween are the location
                         of the embedding files

    Return
    ------
    Unit; writes the covariance matrix to a text file
     */
    val outputDir = args.head
    val inputHyperParam = args.tail.head
    val inputDirs = args.tail.tail

    val conf = new SparkConf()
                    .setMaster("local[*]")
                    .setAppName("embedPca")
    implicit val sc = new SparkContext(conf)

    val hyperParamMapF = getListOfFiles _ andThen getParamMap _
    val hyperParamMap = hyperParamMapF(inputHyperParam)

    val pcaAllWithOutDir = pcaAll(outputDir,hyperParamMap)_
    inputDirs.map{dir => pcaAllWithOutDir(dir)}


  }
}