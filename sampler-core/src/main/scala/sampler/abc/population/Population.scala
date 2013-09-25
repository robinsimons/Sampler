/*
 * Copyright (c) 2012 Crown Copyright 
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

package sampler.abc.population

import scala.annotation.tailrec
import sampler.data.Empirical._
import sampler.run.DetectedAbortionException
import sampler.data.SerialSampleBuilder
import sampler.abc._
import sampler.run.Aborter
import sampler.data.Samplable
import sampler.math.Random
import scala.collection.immutable.ListMap
import sampler.math.Partition

/*
 *  Builds a new population by applying ABC sampling and weighting based 
 *  on the previous population.  Note that the size of the outgoing 
 *  population need not be the same as the incoming.  This is used when
 *  load of discovering particles is spread across different threads.
 *  
 *  This is the object used by 
 */

object Population {
	def apply(model: ABCModel)(
			prevPopulation: model.Population,
			quantity: Int, 
			tolerance: Double,
			aborter: Aborter,
			meta: ABCParameters,
			random: Random
	): model.Population = {
		import model._
		implicit val r = random
		
		val weightsTable = prevPopulation.groupBy(_.value).map{case (k,v) => (k, v.map(_.weight).sum)}.toIndexedSeq
		val (parameters, weights) = weightsTable.unzip
		val samplablePopulation = Samplable.fromPartition(parameters, Partition.fromWeights(weights))
		
		@tailrec
		def nextParticle(failures: Int = 0): Particle[Parameters] = {
			if(aborter.isAborted) throw new DetectedAbortionException("Abort flag was set")
			else if(failures >= meta.particleRetries) throw new RefinementAbortedException(s"Aborted after the maximum of $failures trials")
			else{
				def getScores(params: Parameters): IndexedSeq[Double] = {
					val modelWithMetric = samplableModel(params, observations).map(_.distanceTo(observations))
					val modelWithScores = SerialSampleBuilder(modelWithMetric)(_.size == meta.reps)
						.filter(_ <= tolerance)
					modelWithScores
				}
				
				def getWeight(params: Parameters, numPassed: Int): Option[Double] = {
					val fHat = numPassed.toDouble / meta.reps
					val numerator = fHat * prior.density(params)
					val denominator = weightsTable.map{case (params0, weight) => 
						weight * params0.perturbDensity(params)
					}.sum
					if(numerator > 0 && denominator > 0) Some(numerator / denominator)
					else None	
				}
				
				val res: Option[Particle[Parameters]] = for{
					params <- Some(samplablePopulation.sample().perturb()) if prior.density(params) > 0
					fitScores <- Some(getScores(params))
					weight <- getWeight(params, fitScores.size) 
				} yield(Particle(params, weight, fitScores.min))
				
				res match {
					case Some(p: Particle[Parameters]) => p
					case None => nextParticle(failures + 1)
				}
			}
		}
		
		(1 to quantity).map(i => nextParticle()) 
	}
}