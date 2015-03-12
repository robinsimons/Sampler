package sampler.spike.farrington

import scala.annotation.tailrec
import org.apache.commons.math3.distribution.PoissonDistribution
import scala.util.Random
import breeze.stats.distributions.Poisson
import breeze.stats.distributions.NegativeBinomial
import breeze.stats.distributions.LogNormal

/*
  =========
  NOTES:
  Function to simulate outbreak data for the Early Detection System.
    
  Uses a negative binomial model if dispersion parameter > 1.
  Uses a Poission distribution if dispersion parameter == 1.
  
  Follows the method outlined in
  Noufaily et al., Statist. Med. 2013 (32) 1206-1222
  
  =========
  AUTHOR:
  
  Author:    Teedah Saratoon
  Date:      27/02/2015
  Last edit: 04/03/2015
  
  ==========
  INPUTS:

  nData          No. of time intervals for which to simulate data
  
  alpha           Frequency of reports
  beta            Linear trend
  m               Seasonality (0 = none, 1 = annual, 2 = biannual)
  gamma_1         Controls magnitude of peaks
  gamma_2         Controls magnitude of troughs
  dispersion      Dispersion parameter
  
  k               Parameter which controls the magnitude of the outbreak
  
  outbreakLength  Length of outbreak ("short" or "long")
  endPreOutbreak  End of pre-outbreak period 
  endOutbreak     End of outbreak period
  
  =========
  FUNCTIONS:
  
  stdDev          Calculates standard deviation
  sumFunction     Performs sum of a function of an integer from a to b
  sumTerm         Function of j which is to be summed from j= 1 to m:
  addList         Adds a list of values at the specified indices to a given sequence
  
  =========  
  OUTPUT:
  
  counts          Outbreak counts at each month
  hist            Binned count data corresponding to outbreak
  start           Starting month of outbreak
    
  */

case class GenerationParams(
      alpha: Double,
      beta: Double,
      m: Int,
      gamma_1: Double,
      gamma_2: Double,
      dispersion: Double,
      k: Double
    )
object GenerationParams{
  val scenario14 = GenerationParams(1.5, 0, 1, 0.2, -0.4, 1, 6)
  val scenario1 = GenerationParams(0.1, 0, 0, 0, 0, 1.5, 6)
  val default = scenario14
}

case class GenerationResult(
  year: IndexedSeq[Int],
  month: IndexedSeq[Int],
  baseline: IndexedSeq[Int],
  counts: IndexedSeq[Int],
  hist: List[(Int, Int)],
  start: Int,
  end: Int
  )
  
case class SplitResult(
  data1: GenerationResult,
  data2: GenerationResult
)

object GenerateData {
  
  def run(
      nData: Int,
      endYear: Int,
      outbreakLength: String,
      endPreOutbreak: Int,
      endOutbreak: Int,
      params: GenerationParams = GenerationParams.default
    ): GenerationResult = {
    
    import params._
    
    // Construct sequences of months and years
    val startYear = math.round(endYear - nData.toDouble/12)  
    val year = (1 to nData).map(i => (startYear + ((i-1) / 12)).toInt)
    val month = (1 to nData).map(i => (i-1) % 12 + 1)  
    
    //=======================
    //Simulate baseline data
    
    // Function of j which is to be summed from j= 1 to m:
    def sumTerm(j: Int): Double = {
      gamma_1*math.cos((2*math.Pi*j*nData).toDouble / 12) + 
      gamma_2*math.sin((2*math.Pi*j*nData).toDouble / 12)
    }
    
    // Calculate the mean of the baseline data
		val mean_baseline = math.exp(alpha + beta*nData + sumFunction(sumTerm)(1,m))
    
    // Sample baseline data from a Poisson or Negative Binomial distribution
    val dataBaseline = 
    if (dispersion == 1) {
      val poi = new Poisson(mean_baseline)
      poi.sample(nData)
    }
    else {
      val n = mean_baseline / (dispersion - 1) // number of failures
      val p = 1 - math.pow(dispersion,-1)      // probability of success
      val nb = new NegativeBinomial(n,p)
      nb.sample(nData)
    }
    
    //=======================
    //Simulate outbreak data
    
    val rnd = new Random  
    val tOutbreak = (endPreOutbreak + 1) + rnd.nextInt(endOutbreak - endPreOutbreak)
    
    // Calculate standard deviation of the baseline count at each tOutbreak
    def stdDev(data: IndexedSeq[Int], mean: Double): Double = {
      data.map(x => math.pow(x - mean, 2)).sum / nData
    }
    val sd = stdDev(dataBaseline, mean_baseline)
    
    // Calculate no. of outbreak cases to simulate
    val poi_outbreak = new Poisson(k * sd)
    val nCases = poi_outbreak.sample()
    
    // Distribute over required period (~3 months for short, ~6 months for long)
    val outbreakDistribution = 
      if (outbreakLength == "short")
        LogNormal(0,0.5).sample(nCases).sorted.map(x => math.floor(x).toInt)
      else
        LogNormal(0,0.5).sample(nCases).sorted.map(x => math.floor(2*x).toInt)
    
    // Count number of outbreak cases for each month of the outbreak
    val outbreakHist = 
      outbreakDistribution.groupBy(w => w).mapValues(_.size).toList.sorted
    
    // Create list of pairs of time index and number of cases to be added
    val outbreakIdx =
      outbreakHist.map{case (key, value) => (key+tOutbreak-1, value)}
    
    // Last month of outbreak
    val tEnd = outbreakIdx.last._1 + 1
    
    // Add to baseline data to return simulated outbreak data
    val dataOutbreak = addList(dataBaseline,outbreakIdx)
    
    GenerationResult(year, month, dataBaseline, dataOutbreak, outbreakHist, tOutbreak, tEnd)
        
  }
  
  //=======================
  // Function definitions
  
  // Performs sum of a function of an integer from integer a to integer b
  def sumFunction(f: Int => Double)(a: Int, b: Int): Double = {
    @tailrec
    def loop(a: Int, acc: Double): Double = {
      if (a > b) acc
      else loop(a+1, acc + f(a))
    }
    loop(a,0)    
  }
        
  // Adds a list of values at the specified indices to a given sequence
  def addList(
    current: IndexedSeq[Int],
    toDo: List[(Int, Int)]
  ): IndexedSeq[Int] = {
    @tailrec
    def loop(update: IndexedSeq[Int], toDo: List[(Int, Int)]): IndexedSeq[Int] = {
      if (toDo.size == 0) update
      else loop(update.updated(toDo.head._1, update(toDo.head._1) + toDo.head._2), toDo.tail)
    }
    loop(current,toDo)
  }
  
  def splitOutbreak(data: GenerationResult): SplitResult = {
    
    val outbreakHist = data.hist
    val tOutbreak = data.start
    val nData = data.baseline.length
     
    def splitData(list: List[(Int, Int)]) = {
      val rnd = new Random
      val count1 = list.map{ 
        case (key, value) => (key, rnd.nextInt(value + 1))
      }
      val count1_indexed = count1.zipWithIndex
      val count2_indexed = count1_indexed.map{
        case ((key, value), i) => ((key, list(i)._2 - value), i)
      }
      val (count2, index) = count2_indexed.unzip
      
      (count1, count2)
      
    }
    
    val (baseline1, baseline2) =
      splitData((1 to nData).map(i => (i,data.baseline(i-1))).toList)
    val dataBaseline1 = baseline1.map(i => i._2).toIndexedSeq
    val dataBaseline2 = baseline2.map(i => i._2).toIndexedSeq
    
    val (count1, count2) = splitData(outbreakHist)    
    val outbreakIdx1 =
      count1.map{case (key, value) => (key+tOutbreak-1, value)}    
    val outbreakIdx2 =
      count2.map{case (key, value) => (key+tOutbreak-1, value)}
       
    val dataOutbreak1 = addList(dataBaseline1,outbreakIdx1)
    val dataOutbreak2 = addList(dataBaseline2,outbreakIdx2)
    
    val data1 = GenerationResult(
      data.year,
      data.month,
      dataBaseline1,
      dataOutbreak1,
      count1,
      data.start,
      data.end
    )
      
    val data2 = GenerationResult(
      data.year,
      data.month,
      dataBaseline2,
      dataOutbreak2,
      count2,
      data.start,
      data.end
    )
      
    SplitResult(data1, data2)
    
  }
      

}