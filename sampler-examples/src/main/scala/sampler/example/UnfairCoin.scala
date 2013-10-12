/*
 * Copyright (c) 2012-2013 Crown Copyright 
 *                    Animal Health and Veterinary Laboratories Agency
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sampler.example

import java.nio.file.{Files, Paths}
import sampler.abc._
import sampler.abc.population._
import sampler.data.Empirical._
import sampler.data.Samplable
import sampler.math._
import sampler.r.QuickPlot._
import sampler.cluster.actor.ClusterNode
import sampler.cluster.abc.population.PopulationExecutor
import sampler.cluster.abc.population.DispatchingPopulationBuilder
import sampler.cluster.actor.PortFallbackSystem

object UnfairCoinApplication extends App 
	with UnfairCoinFactory 
	with UnfairCoin
	
object UnfairCoinWorker extends App {
	new ClusterNode(new PopulationExecutor(CoinModel, Random))
}

trait UnfairCoinFactory{
	val meta = new ABCParameters(
    	reps = 10000,
		numParticles = 100, 
		refinements = 10,
		particleRetries = 100, 
		particleChunking = 50
	)
	val abcMethod = new ABCMethod(CoinModel, meta, Random)
	
//	val pBuilder = DispatchingPopulationBuilder(PortFallbackSystem("ClusterSystem"))
	val pBuilder = LocalPopulationBuilder()
}

trait UnfairCoin {
	val pBuilder: PopulationBuilder
	
	implicit val abcMethod: ABCMethod[CoinModel.type]

	val population0 = abcMethod.init
	
	//TODO Fix slightly nasty mapping to population values
	val finalPopulation = abcMethod.run(population0, pBuilder).map(_.map(_.value))

	val headsDensity = finalPopulation.get.map(_.pHeads)
	
	val wd = Paths.get("results", "UnfairCoin")
	Files.createDirectories(wd)
	
	writeDensity(
			wd, 
			"posterior", 
			headsDensity.continuousVariable("P[Heads]")
	)
}

object CoinModel extends CoinModelBase {
  val abcRandom = Random
  val modelRandom = Random
  val statistics = StatisticsComponent
}

trait CoinModelBase extends ABCModel with Serializable{
	implicit val abcRandom: Random
  	val modelRandom: Random

	val observations = Observations(10,7)
	
    case class Parameters(pHeads: Double) extends ParametersBase with Serializable{
      lazy val kernel = Samplable.normal(0,0.5)
      
      def perturb() = Parameters(pHeads + kernel.sample())
      def perturbDensity(that: Parameters) = kernel.density(pHeads - that.pHeads)
    }

    case class Observations(numTrials: Int, numHeads: Int) extends ObservationsBase with Serializable{
    	assert(numTrials >= numHeads)
    	def proportionHeads = numHeads.asInstanceOf[Double] / numTrials
    }
    
    case class Output(simulated: Observations) extends OutputBase with Serializable{
      def distanceTo(obs: Observations): Double = {
    	assert(simulated.numTrials == obs.numTrials)
      	math.abs(simulated.numHeads - obs.numHeads)
      }
    }
    
    def samplableModel(p: Parameters, obs: Observations) = new Samplable[Output] with Serializable{
      val r = modelRandom
      override def sample() = {
        def coinToss() = r.nextBoolean(Probability(p.pHeads))
        Output(Observations(obs.numTrials, (1 to obs.numTrials).map(i => coinToss).count(identity)))
      }
    }
    
    val prior = new Prior[Parameters] with Serializable{
	    def density(p: Parameters) = {
	      if(p.pHeads > 1 || p.pHeads < 0) 0.0
	      else 1.0
	    }
	    
	    def sample() = Parameters(abcRandom.nextDouble(0.0, 1.0))
    }
}