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

package sampler.abc

import scala.annotation.tailrec
import sampler.data.Empirical._
import sampler.run.JobRunner
import sampler.run.Job
import sampler.math.Probability
import sampler.data.SerialSampleBuilder
import sampler.data.Empirical
import java.io.FileOutputStream
import java.io.ObjectOutputStream
import sampler.math.Random
import sampler.run.Aborter
import sampler.run.UserInitiatedAbortException
import scala.util.Try

class ABCMethod[M <: ABCModel](val model: M) extends Serializable{
  import model._
	
	def init: Population = {
		val numParticles = model.meta.numParticles
		(1 to numParticles).par.map(i => Particle(model.prior.sample(), 1.0, Double.MaxValue)).seq
	}
	
	private def generateParticles(
			samplablePop: Empirical[Parameters],
			quantity: Int, 
			tolerance: Double,
			aborter: Aborter
	): Population = {
		@tailrec
		def nextParticle(failures: Int = 0): Particle[Parameters] = {
			if(aborter.isAborted) 
				throw new UserInitiatedAbortException("Abort flag was set")
			else if(failures >= meta.particleRetries) 
				throw new UserInitiatedAbortException(s"Aborted after the maximum of $failures trials")
			else{
				def getScores(params: Parameters) = {
					val modelWithMetric = samplableModel(params, observations).map(_.distanceTo(observations))
					val modelWithScores = SerialSampleBuilder(modelWithMetric)(_.size == meta.reps)
						.filter(_ <= tolerance)
					modelWithScores
				}
				
				def getWeight(params: Parameters, numPassed: Int) = {
					val fHat = numPassed.toDouble / meta.reps
					val numerator = fHat * prior.density(params)
					val denominator = samplablePop.probabilities.map{case (params0, probability) => 
						probability.value * params0.perturbDensity(params)
					}.sum
					if(numerator > 0 && denominator > 0) Some(numerator / denominator)
					else None	
				}
				
				val res: Option[Particle[Parameters]] = for{
					params <- Some(samplablePop.sample().perturb()) if prior.density(params) > 0
					fitScores <- Some(getScores(params))// if scores.size > 0
					//_ = println("---"+fitScores)
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
	
	def evolveOnce(
		  pop: Population, 
			runner: JobRunner,
			tolerance: Double
	)(implicit r: Random): Option[Population] = {
		import model.meta
		
		println("Now working on tolerance = "+tolerance)
		
		// Number of particles to be generated per job?
		val jobSizes = (1 to meta.numParticles)
			.grouped(meta.particleChunking)
			.map(_.size).toList
		println(s"JobSizes: $jobSizes")
		
		// Prepare samplable Parameters from current population
		val samplable: Empirical[Parameters] = pop.groupBy(_.value).map{case (k,v) => (k,v.map(_.weight).sum)}.toEmpiricalWeighted
		
		val jobs = jobSizes.map(numParticles => Job{(aborter:Aborter) =>
			generateParticles(samplable, numParticles, tolerance, aborter)
		}).toList
		val runnerResults: Seq[Try[Population]] = runner.apply(jobs)
		//val t = Try(runnerResults.map(_.get))
		
		if(runnerResults.contains()) None else Some()
		
	    // TODO: Assertion belongs in generateParticles
	    //assert(runnerResults.size == meta.numParticles)
	
	    if(runnerResults.contains(None)) None else Some(runnerResults.flatMap(_.get))
	}
		
	def run(
			pop: Population, 
			runner: JobRunner
	)(implicit r: Random): Option[Population] = {
		import model.meta
		
		@tailrec
		def refine(
				pop: Population, 
				numAttempts: Int, 
				currentTolerance: Double,
				previousTolerance: Double
		): Option[Population] = {
			println("Generations left to go "+numAttempts)
			//TODO report a failure ratio at the end of a generation
			if(numAttempts == 0) Some(pop)
			else{
				evolveOnce(pop, runner, currentTolerance) match {
					case None =>
						println(s"Failed to refine current population, evolving within previous tolerance $previousTolerance")
						refine(pop, numAttempts - 1, previousTolerance, previousTolerance)
					case Some(newPop) =>
						//Next tolerance is the median of the previous best for each particle
						val fit = newPop.map(_.bestFit)
						val medianTolerance = model.statistics.quantile(newPop.map(_.bestFit).toEmpiricalSeq, Probability(0.5))
						refine(newPop, numAttempts - 1, medianTolerance, currentTolerance)
				}
			}
		}
		
		refine(pop, meta.refinements, meta.tolerance,  meta.tolerance)
	}
}
