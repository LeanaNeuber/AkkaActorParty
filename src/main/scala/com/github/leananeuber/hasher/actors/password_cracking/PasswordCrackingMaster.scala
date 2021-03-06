package com.github.leananeuber.hasher.actors.password_cracking

import akka.actor.{Actor, ActorLogging, ActorRef, PoisonPill, Props, Terminated}
import com.github.leananeuber.hasher.Settings
import com.github.leananeuber.hasher.actors.Reaper
import com.github.leananeuber.hasher.actors.password_cracking.PasswordCrackingProtocol._
import com.github.leananeuber.hasher.protocols.MasterWorkerProtocol.{MasterActor, MasterHandling}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps


object PasswordCrackingMaster {

  val name = "pc-master"

  def props(nWorkers: Int, session: ActorRef): Props = Props(new PasswordCrackingMaster(nWorkers, session))

}


class PasswordCrackingMaster(val nWorkers: Int, val session: ActorRef) extends MasterActor with MasterHandling {

  private val settings = Settings(context.system)

  val passwordRange: Range = settings.passwordRangeStart to settings.passwordRangeEnd
  val partitionSize: Int = settings.linearizationPartitionSize

  val receivedResponses: mutable.Map[ActorRef, Map[Int, Int]] = mutable.Map.empty
  var counter: Long = 0L

  override def preStart(): Unit = {
    log.info(s"Starting $name")
    Reaper.watchWithDefault(self)
  }

  override def postStop(): Unit = {
    log.info(s"Stopping $name and all associated workers")
    workers.foreach(_ ! PoisonPill)
  }

  override def receive: Receive = handleWorkerRegistrations orElse {
    case StartCrackingCommand(secrets) =>
      if(workers.size < nWorkers) {
        // delay processing of message until all workers are ready
        context.system.scheduler.scheduleOnce(1 second, self, StartCrackingCommand(secrets))

      } else {
        val workPackages = splitWork(passwordRange)
        distributeWork(workPackages, secrets)
      }

    case PasswordsCrackedEvent(passwords) =>
      log.info(s"received ${passwords.size} passwords from $sender")
      receivedResponses(sender) = passwords
      if(receivedResponses.size == workers.size) {
        val combinedPasswordMap = receivedResponses.values.reduce(_ ++ _)
        session ! PasswordsCrackedEvent(combinedPasswordMap)
      }

    case StartCalculateLinearCombinationCommand(passwords) =>
      workers.foreach( ref => {
          ref ! CalculateLinearCombinationCommand(passwords, counter)
          counter += 1
      })

    case NoCombinationFound(passwords) =>
      if(counter < Long.MaxValue/partitionSize) {
        sender ! CalculateLinearCombinationCommand(passwords, counter)
        counter += 1
      }

    case LinearCombinationCalculatedEvent(combination) =>
      counter = Long.MaxValue/partitionSize
      log.info(s"received prefixes from $sender")
      session ! LinearCombinationCalculatedEvent(combination)

    // catch-all case: just log
    case m =>
      log.warning(s"received unknown message: $m")
  }

  def distributeWork(workPackages: Seq[Seq[Int]], secrets: Map[Int, String]): Unit = {
    workers.zip(workPackages).foreach{ case (ref, packages) =>
      ref ! CrackPasswordsCommand(secrets, packages)
    }
  }
}
