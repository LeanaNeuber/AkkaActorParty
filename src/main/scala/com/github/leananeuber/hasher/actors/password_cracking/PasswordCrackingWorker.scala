package com.github.leananeuber.hasher.actors.password_cracking

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import com.github.leananeuber.hasher.MasterWorkerProtocol.{RegisterWorker, RegisterWorkerAck}
import com.github.leananeuber.hasher.actors.Reaper
import com.github.leananeuber.hasher.actors.password_cracking.PasswordCrackingProtocol.{CrackPasswordsCommand, PasswordsCrackedEvent}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps


object PasswordCrackingWorker {

  val passwordRange: Range = 0 to 1000000

  def props(master: ActorRef): Props =
    Props(new PasswordCrackingWorker(master))

  case class CrackingFailedException(m: String) extends RuntimeException(m)

}


class PasswordCrackingWorker(master: ActorRef) extends Actor with ActorLogging {
  import PasswordCrackingWorker._

  val name: String = self.path.name
  val registerWorkerCancellable: Cancellable =
    context.system.scheduler.schedule(0 seconds, 5 seconds, master, RegisterWorker)

  override def preStart(): Unit = {
    log.info(s"Starting $name")
    Reaper.watchWithDefault(self)
  }

  override def postStop(): Unit =
    log.info(s"Stopping $name")

  override def receive: Receive = {
    case RegisterWorkerAck =>
      registerWorkerCancellable.cancel()
      log.info(s"$name: successfully registered at master actor")

    case CrackPasswordsCommand(secrets) =>
      log.info(s"$name: working on ${secrets.keys} passwords")
      sender() ! PasswordsCrackedEvent(decrypt(secrets))

    // catch-all case: just log
    case m =>
      log.warning(s"$name: Received unknown message: $m")
  }

  def decrypt(secrets: Map[Int, String]): Map[Int, Int] = secrets.map( idHashTuple => {
    val realValue = unhash(idHashTuple._2) match {
      case Some(real) => real
      case None => throw CrackingFailedException("Could not decrypt password hash")
    }
    idHashTuple._1 -> realValue
  })

  private def unhash(hexHash: String): Option[Int] = passwordRange
    .map ( i => i -> hash(i) )
    .find{ case (_, h) => h.equals(hexHash) }
    .map ( indexHashTuple => indexHashTuple._1 )

  private def hash(number: Int) = {
    val digest = MessageDigest.getInstance("SHA-256")
    val hashedBytes = digest.digest(String.valueOf(number).getBytes(StandardCharsets.UTF_8))

    hashedBytes.map( byte =>
      ((byte & 0xff) + 0x100).toHexString.substring(1)
    ).mkString("")
  }
}
