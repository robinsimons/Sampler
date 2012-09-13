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
import scala.annotation.tailrec

trait Samplable[A]{ self =>
	def sample(implicit r: Random): A

	def until(condition: IndexedSeq[A] => Boolean)(implicit r: Random) = new Samplable[IndexedSeq[A]]{
		def sample(implicit r: Random): IndexedSeq[A] = {
			@tailrec
			def append(previous: IndexedSeq[A]): IndexedSeq[A] = {
				if(condition(previous)) previous
				else append(previous.:+(self.sample))
			}
			append(IndexedSeq[A](self.sample))
		}
	}
	def filter(predicate: A => Boolean) = new Samplable[A]{
		@tailrec
		override def sample(implicit r: Random): A = {
			val s = self.sample(r)
			if(predicate(s)) s
			else sample(r)
		}
	}
}
