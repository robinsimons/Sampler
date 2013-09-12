package sampler.r

import sampler.data.Empirical
import java.nio.file.Paths
import sampler.io.table.CSVTableWriter
import sampler.io.table.Types.Column
import java.nio.file.Path
import sampler.data.EmpiricalSeq
import sampler.math.Probability
import sampler.data.EmpiricalTable

object QuickPlot {
	//TODO add an option for quickly plotting a single data set without bothering with Map
	def writeDensity(path: Path, fileName: String, data: Map[String, Seq[Double]]) = {
	    def rScriptBuilder(name: String) = {
	      val builder = new StringBuilder
	      builder.append("require(ggplot2)\n")
	      builder.append("require(reshape)\n")
	      
	      builder.append("pdf(\"" + name + ".pdf\", width=8.27, height=5.83)\n")
	      
	      builder.append("data <- read.csv(\"" + name + ".csv\")\n")
	      builder.append("melted = melt(data)\n")
	      builder.append("ggplot(melted, aes(x=value, colour=variable)) + geom_density()\n")

	      builder.append("dev.off()\n")
	      
	      builder.toString
	    }
	    
	    val columns = data.map{case (name, values) => new Column(values, name)}.toSeq
	    
	    val writer  = new CSVTableWriter(path.resolve(fileName + ".csv"), true)
	    writer.apply(columns:_*)
	    
	    val rScript = rScriptBuilder(fileName.toString)
	    
	    ScriptRunner(rScript, path.resolve(fileName))
	  }
	
	
	//TODO
	//Consider making quickPlot able to plot from either
	// a) sequences (implemented above), and
	// b) EmpiricalTable/EmpiricalWeighted.
	//
	//Not sure if the latter is necessary.  It's
	//a bit more complicated, but AG had a method which 
	//seemed to work, based on the below
    
	/*
	def expand(key: Double, repeats: Int) = {
        (1 to repeats).map(a => key).toList
      }
      
    def transformToDist(map: Map[Double, Probability]) = {
        val min = map.map(pair => pair._2.value).toList.min
        val normalised = map.map(pair => pair._1 -> (pair._2.value / min).round.toInt)
        normalised.flatMap(k => expand(k._1, k._2)).toSeq
    }
    */
}
