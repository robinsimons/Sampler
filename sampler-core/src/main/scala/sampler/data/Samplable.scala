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

import scalaz._
import Scalaz._
import sampler.math.Random

trait Samplable[R, T]{
	type Counts = Map[T, Int]
		
	val items: R
	def numRemaining: Int
	def empty: State[R, Counts]
	
	def draw(n: Int = 1)(implicit r: Random): (R, Counts) = {
		val state = drawState(n)
		state(items)
	}

	/*
	 * State which removes a single item
	 */
	protected def removeOne(soFar: Counts)(implicit r: Random): State[R, Counts]
	
	/*
	 * State which removes n items
	 */
	protected def drawState(n: Int = 1)(implicit r: Random): State[R, Counts] = for(
		selection <- {
			assert(numRemaining >= n)	//Check the table has enough left
			(1 to n).foldLeft(empty)((accState, _) => accState.flatMap(removeOne))
		}
	) yield selection
	
}

trait ToSamplable {
	implicit class SamplableMap[T](val items: Map[T, Int]) extends Samplable[Map[T,Int], T]{
		def empty = state[Counts, Counts](Map[T, Int]())
		def numRemaining = items.values.sum
			
		def removeOne(soFar: Counts)(implicit r: Random): State[Counts, Counts] = for(
			item <- State[Counts, T]{s =>
				val (colours, counts) = s.toIndexedSeq.unzip
				val ballIndex = r.nextInt(s.values.sum)
				val selected: T = colours(
					counts.tail
						.view
						.scanLeft(counts(0))(_ + _)
						.indexWhere(_ >= ballIndex)
				)
				(s.updated(selected, s(selected) - 1), selected)
			}
		) yield soFar.updated(item, soFar.getOrElse(item, 0) + 1)
	}
	
	implicit class SamplableSeq[T](val items: IndexedSeq[T]) extends Samplable[IndexedSeq[T], T]{
		type Remain = IndexedSeq[T]
		
		def empty = state[Remain, Counts](Map[T, Int]())
		def numRemaining = items.size
		
		def removeOne(soFar: Counts)(implicit r: Random): State[Remain, Counts] = for(
			remaining <- get[Remain];
			index = r.nextInt(remaining.size);
			item <- state(remaining(index));
			_ <- put(remaining.patch(index, Nil, 1))
		) yield soFar.updated(item, soFar.getOrElse(item, 0) + 1)
	}
}