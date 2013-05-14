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

import scala.util.Try
import sampler.abc.ABCModel
import sampler.data.Empirical
import sampler.run.local.ParallelCollectionRunner
import sampler.run.local.Abortable
import sampler.run.local.LocalRunner
import sampler.abc.ABCParameters
import sampler.math.Random

class LocalPopulationBuilder(runner: LocalRunner) extends PopulationBuilder{
	def run(model: ABCModel)(
			pop: Empirical[model.Parameters], 
			jobSizes: Seq[Int], 
			tolerance: Double, 
			meta: ABCParameters,
			random: Random
		): Seq[Try[model.Population]] = {
		val jobs = jobSizes.map{quantity => Abortable{aborter =>
			Population(model)(pop, quantity, tolerance, aborter, meta, random)
		}}
		
		runner.apply(jobs)
	}
}
object LocalPopulationBuilder{
	def apply() = new LocalPopulationBuilder(ParallelCollectionRunner())
}