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

import org.scalatest.junit.AssertionsForJUnit
import org.junit.Test
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import sampler.math.Statistics
import sampler.abc.builder.PopulationBuilder
import sampler.math.Random
import sampler.abc.generation.IntegerModel
import org.junit.Before
import sampler.abc.generation.IntegerModel

class ABCMethodTest extends AssertionsForJUnit with MockitoSugar{

  val model = IntegerModel
  
  val instance = new ABCMethod{
	val initialise = mock[Initialise]
	val iterate = mock[Iterate]
	val statistics = mock[Statistics]
  }
  
  val parameters = mock[ABCParameters]
  val pBuilder = mock[PopulationBuilder]
  val r = mock[Random]

  val ePop = EncapsulatedPopulation(model)(Nil)
  
  @Test def initialisesAndIteratesToGiveOptionPopulation {
    val mock1 = mock[model.Parameters]
    val mock2 = mock[model.Parameters]
    val mock3 = mock[model.Parameters]

    val expectedReturn = Some(Seq(mock1, mock2, mock3))
		
    val p1 = Particle(mock1, 10.0, Double.MaxValue)
    val p2 = Particle(mock2, 15.0, Double.MaxValue)
    val p3 = Particle(mock3, 20.0, Double.MaxValue)

    val ePop2 = EncapsulatedPopulation(model)(Seq(p1, p2, p3))
		
    when(instance.initialise.apply(model, parameters)).thenReturn(ePop)
		
    when(instance.iterate.apply(ePop, parameters, pBuilder, r)).thenReturn(Some(ePop2))
		
    val evolvedPopulation = instance.apply(model, parameters, pBuilder, r)

    assert(expectedReturn === evolvedPopulation)
  }
  
  @Test def returnsNoneWhenANoneParticleIsGiven {
    when(instance.initialise.apply(model, parameters)).thenReturn(ePop)
    
    when(instance.iterate.apply(ePop, parameters, pBuilder, r)).thenReturn(None)
    
    assert(instance.apply(model, parameters, pBuilder, r) === None)
  }
  
  @Test def ensureTwoParameterMocksAreDifferent {
    val p1 = mock[IntegerModel.Parameters]
    
    assert(p1 != mock[IntegerModel.Parameters])
  }
}