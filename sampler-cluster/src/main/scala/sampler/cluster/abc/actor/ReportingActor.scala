package sampler.cluster.abc.actor

import akka.actor.ActorLogging
import akka.actor.Actor
import akka.actor.actorRef2Scala

class ReportingActor[P](action: Option[Report[P] => Unit]) extends Actor with ActorLogging {
	def receive = {
		case report: Report[P] =>
			action.foreach(a => a(report))
			sender ! ReportCompleted(report)
		case msg => log.error("Unexepected message ': {}", msg.getClass)
	}
}

