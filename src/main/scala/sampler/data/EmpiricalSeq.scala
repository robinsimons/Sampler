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

package sampler.data

import sampler.math.Random
import scala.collection.GenTraversableOnce
import sampler.math.Probability

/*
 * Empirical implementation which is backed by an IndexedSeq.  Ideal for
 * collecting observations from a continuous observation.
 */
class EmpiricalSeq[A](val seq: IndexedSeq[A]) extends Empirical[A]{ self =>
	def sample(implicit r: Random) = seq(r.nextInt(size))
	
	private lazy val size = seq.size
	
	lazy val supportSize = seq.groupBy(identity).keys.size
	
	lazy val probabilities = {
		val sizeAsDouble = size.asInstanceOf[Double]
		seq.groupBy(identity).map{case (k,v) => (k, Probability(v.size / sizeAsDouble))}
	}
	def ++(more: GenTraversableOnce[A]) = new EmpiricalSeq(seq ++ more)
}	