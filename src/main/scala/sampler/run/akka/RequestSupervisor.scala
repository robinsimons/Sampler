package sampler.run.akka

import akka.actor.Actor
import akka.actor.ActorRef
import scala.concurrent.duration._
import scala.util.Try
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.MemberRemoved
import akka.actor.RootActorPath
import akka.cluster.Member
import akka.actor.ActorLogging

class RequestSupervisor extends Actor with ActorLogging{
	val confirmationTimeout = 1.second
	case class ConfirmationReminder()
	
	val cluster = Cluster(context.system)
  	override def preStart(): Unit = cluster.subscribe(self, classOf[MemberRemoved])
	override def postStop(): Unit = cluster.unsubscribe(self)
	
	def receive = {
		case Delegate(request,worker) => 
			log.info("Delegating request {}", request.jobID)
			worker ! request
			import context.dispatcher
			context.system.scheduler.scheduleOnce(confirmationTimeout, self, ConfirmationReminder)
			context.become(awaitingConfirmation(worker, request))
	}
	
	def awaitingConfirmation(worker: ActorRef, request: Request): Receive = {
		case Abort =>
			worker ! Abort
			context.stop(self)
		case WorkConfirmed =>
			log.info("Work confirmed")
			context.become(awaitingResults(worker, request)) 
		case ConfirmationReminder => 
			log.info("Confirmation reminder")
			failed(request)
		case MemberRemoved(m) =>
			val workerPath = Seq("user", "worker")
			val potentialWorker = context.actorFor(RootActorPath(m.address) / workerPath)
			if(worker == potentialWorker) {
				log.info("Member {} removed", m)
				failed(request)
			}
		case WorkRejected => failed(request)
	}
	
	private def failed(request: Request){
		log.info("Failed, resubmitting request")
		context.parent.tell(request.job, request.requestor)
		context.stop(self)
	}
	
	private def clusterMemberLost(myWorker:ActorRef, member: Member, request: Request){
		val workerPath = Seq("user", "worker")
		val potentialWorker = context.actorFor(RootActorPath(member.address) / workerPath)
		if(myWorker == potentialWorker) {
			log.info("Cluster member lost during calculation")
			failed(request)
		}
	}
	
	def awaitingResults(worker: ActorRef, request: Request): Receive = {
		case MemberRemoved(m) => clusterMemberLost(worker, m, request)
		case Abort =>
			worker ! Abort
			context.stop(self)
		case result: Try[_] =>
			request.requestor ! result
			//TODO better mechanism for new work after job completeion 
			log.info("Job done")
			context.parent.tell(WorkerIdle, worker)
			context.stop(self)
	}
}