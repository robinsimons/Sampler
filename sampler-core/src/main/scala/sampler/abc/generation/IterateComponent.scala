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

package sampler.abc.generation

import sampler.math.StatisticsComponent
import sampler.abc.builder.PopulationBuilder
import scala.annotation.tailrec
import sampler.io.Logging
import sampler.abc.ABCModel
import sampler.abc.EncapsulatedPopulation
import sampler.Implicits._
import sampler.math.Probability
import sampler.abc.ABCParameters
import sampler.math.Random

protected[abc] trait IterateComponent { 
	self: Logging with StatisticsComponent => 
	val iterate: Iterate
	
	trait Iterate {
		def apply[M <: ABCModel](
				ePop: EncapsulatedPopulation[M], 
				abcParams: ABCParameters, 
				populationBuilder: PopulationBuilder,
				random: Random
		): Option[EncapsulatedPopulation[M]] = {
			@tailrec
			def loop(
					ePop: EncapsulatedPopulation[M], 
					generationsRemaining: Int,
					currentTolerance: Double,
					previousTolerance: Double
			): Option[EncapsulatedPopulation[M]] = {
				log.info(generationsRemaining + " generations remaining")
				if(generationsRemaining == 0) Some(ePop)
				else{
					populationBuilder.run(ePop, abcParams, currentTolerance, random) match {
						case None =>
							log.warn(s"Failed to refine current population, evolving within previous tolerance $previousTolerance")
							loop(ePop, generationsRemaining - 1, previousTolerance, previousTolerance)
						case Some(newEPop) =>
							//Next tolerance is the median of the previous best for each particle
							val fit = newEPop.population.map(_.meanFit)
							val medianMeanFit = statistics.quantile(fit.toEmpiricalSeq, Probability(0.5))
							val newTolerance = 
								if(medianMeanFit == 0) {
									log.warn("Median of mean scores from last generation evaluated to 0, half the previous tolerance will be used instead.")
									currentTolerance / 2
								}
								else math.min(medianMeanFit, currentTolerance)
							loop(newEPop, generationsRemaining - 1, newTolerance, currentTolerance)
					}
				}
			}
			
			loop(ePop, abcParams.refinements, abcParams.startTolerance, abcParams.startTolerance)
		}
	}
}