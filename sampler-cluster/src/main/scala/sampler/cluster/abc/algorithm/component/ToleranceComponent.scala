/*
 * Copyright (c) 2012-13 Crown Copyright 
 *                       Animal Health and Veterinary Laboratories Agency
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

package sampler.cluster.abc.algorithm.component

import akka.actor.Actor
import akka.actor.ActorLogging
import sampler.cluster.abc.Weighted
import sampler.math.StatisticsComponent
import sampler.Implicits._
import akka.event.LoggingAdapter
import sampler.cluster.abc.actor.LoggingAdapterComponent

trait ToleranceCalculatorComponent {
	this: StatisticsComponent with LoggingAdapterComponent =>
		
	val toleranceCalculator: ToleranceCalculator
	
	trait ToleranceCalculator {
		def apply[P](weighedParameters: Seq[Weighted[P]], currentTolerance: Double): Double = {
			val medianMeanScore = statistics.quantile(weighedParameters.map{_.meanRepScore}.toEmpiricalSeq, 0.5)
			if(medianMeanScore == 0) {
				logg.warning("Median of mean scores from last generation evaluated to 0. Will use old tolerance again.")
				currentTolerance
			} else if(medianMeanScore > currentTolerance) {
				logg.warning("Median of mean scores from last generation greater than old tolerance. Will use old tolerance again.")
				currentTolerance
			}
			else medianMeanScore
		}
	}
}