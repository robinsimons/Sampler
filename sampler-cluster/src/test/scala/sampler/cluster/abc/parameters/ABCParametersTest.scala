package sampler.cluster.abc.parameters

import org.scalatest.FreeSpec
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._

class ABCParametersTest extends FreeSpec {

	val fullConfig = ConfigFactory.parseString("""
		sampler.abc {
			job {
				replicates = 1000
				particles = 2000
				generations = 50
			}
			algorithm {
				particle-retries = 100
				particle-chunk-size = 200
			}
			cluster {
				particle-memory-generations = 2
				terminate-at-target-generation = true
				futures-timeout = 10 hour
				mixing {
					rate = 500 milliseconds
					num-particles = 150
					response-threshold = 400 milliseconds
				}
			}
		}	
	""")
	
	val instance = ABCParameters.fromConfig(fullConfig)
	val altInstance = ABCParameters.fromConfig(
		ConfigFactory.parseString(
			"sampler.abc.cluster.terminate-at-target-generation = false"
		).withFallback(fullConfig)
	)
	
	"Should parse" - {
		"Job parameters" in {
			assertResult(1000)(instance.job.numReplicates)
			assertResult(2000)(instance.job.numParticles)
			assertResult(50)(instance.job.numGenerations)
		}
		"Algorithm parameters" in {
			assertResult(100)(instance.algorithm.maxParticleRetries)
			assertResult(200)(instance.algorithm.particleChunkSize)
		}
		"Cluster parameters" in {
			assertResult(2)(instance.cluster.particleMemoryGenerations)
			assertResult(true)(instance.cluster.terminateAtTargetGenerations)
			assertResult(false)(altInstance.cluster.terminateAtTargetGenerations)
			assertResult(10.hour.toMillis)(instance.cluster.futuresTimeoutMS)
			assertResult(500)(instance.cluster.mixRateMS)
			assertResult(150)(instance.cluster.mixPayloadSize)
			assertResult(400)(instance.cluster.mixResponseTimeoutMS)
		}
	}
}