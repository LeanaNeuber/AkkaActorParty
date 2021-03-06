package com.github.leananeuber.hasher.actors

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, OneForOneStrategy, Props, SupervisorStrategy}
import com.github.leananeuber.hasher.actors.gene_partners.{MatchGenePartnerMaster, MatchGenePartnerWorker}
import com.github.leananeuber.hasher.actors.hash_mining.HashMiningWorker
import com.github.leananeuber.hasher.actors.password_cracking.{PasswordCrackingMaster, PasswordCrackingWorker}
import com.github.leananeuber.hasher.actors.password_cracking.PasswordCrackingWorker.CrackingFailedException
import com.github.leananeuber.hasher.protocols.MasterWorkerProtocol.SetupConnectionTo
import com.github.leananeuber.hasher.protocols.SessionSetupProtocol.{RegisterAtSession, RegisteredAtSessionAck, SetupSessionConnectionTo}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

object WorkerManager {

  val workerManagerName = "workermanager"

  def props(nWorkers: Int): Props = Props(new WorkerManager(nWorkers))

}


class WorkerManager(nWorkers: Int) extends Actor with ActorLogging {
  import WorkerManager._

  val pc_workers: Seq[ActorRef] = (0 until nWorkers).map{ id =>
    val worker = context.actorOf(PasswordCrackingWorker.props, s"pc-worker-$id")
    context.watch(worker)
    worker
  }
  val mgp_worker: Seq[ActorRef] = (0 until nWorkers).map{ id =>
    val worker = context.actorOf(MatchGenePartnerWorker.props, s"mgp-worker-$id")
    context.watch(worker)
    worker
  }
  val hm_workers: Seq[ActorRef] = (0 until nWorkers).map{ id =>
    val worker = context.actorOf(HashMiningWorker.props, s"hm-worker-$id")
    context.watch(worker)
    worker
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy(3, 10 seconds){
    case _: CrackingFailedException => Restart
  }

  override def preStart(): Unit = {
    log.info(s"Starting $workerManagerName")
    Reaper.watchWithDefault(self)
  }

  override def postStop(): Unit =
    log.info(s"Stopping $workerManagerName")

  def receive: Receive = setup

  def setup: Receive = {
    case SetupSessionConnectionTo(address) =>
      val sessionSelection = context.system.actorSelection(s"$address/user/${Session.sessionName}")
      val registerCancellable = context.system.scheduler.schedule(Duration.Zero, 5 seconds) {
        sessionSelection ! RegisterAtSession(nWorkers)
      }

      context.children.foreach(_ ! SetupConnectionTo(address))
      context.become(waitingForSetupAck(registerCancellable))
  }

  def waitingForSetupAck(registerCancellable: Cancellable): Receive = {
    case RegisteredAtSessionAck =>
      log.info(s"Successfully registered at master node: $sender")
      registerCancellable.cancel()
      context.become(ready(sender))
  }

  def ready(sessionActor: ActorRef): Receive = {
    case m => log.info(s"$workerManagerName received a message: $m")
  }
}
