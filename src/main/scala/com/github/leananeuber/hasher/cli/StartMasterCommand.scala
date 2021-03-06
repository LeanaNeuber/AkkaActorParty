package com.github.leananeuber.hasher.cli

import java.io.File

import akka.actor.Address
import akka.cluster.Cluster
import com.github.leananeuber.hasher.HasherActorSystem
import com.github.leananeuber.hasher.actors.{Reaper, Session, WorkerManager}
import com.github.leananeuber.hasher.protocols.SessionSetupProtocol.SetupSessionConnectionTo
import org.backuity.clist

import scala.language.postfixOps


object StartMasterCommand extends clist.Command(
    name = "master",
    description = "start a master actor system") with CommonStartCommand {

  val masterRole = "master"

  var nSlaves: Int = clist.opt[Int](
    name = "slaves",
    description = "number of slave actor systems to wait for",
    default = CommonStartCommand.defaultNSlaves
  )

  var input: File = clist.arg[File](
    name = "input",
    description = "path to the input CSV file"
  )

  override def defaultPort: Int = CommonStartCommand.defaultMasterPort

  override def run(actorSystemName: String): Unit = {
    val masterHost = host
    val masterPort = port
    val system = HasherActorSystem.actorSystem(actorSystemName, HasherActorSystem.configuration(
      actorSystemName,
      masterRole,
      host,
      port,
      masterHost,
      masterPort
    ))
    val cluster = Cluster(system)

    // TODO: handle cluster events

    // TODO: start processing
    cluster.registerOnMemberUp{
      // create actors
      val reaper = system.actorOf(Reaper.props, Reaper.reaperName)
      val session = system.actorOf(Session.props(nSlaves, input), Session.sessionName)
      val workerManager = system.actorOf(WorkerManager.props(nWorkers), WorkerManager.workerManagerName)

      // start processing
      workerManager ! SetupSessionConnectionTo(Address("akka", actorSystemName, host, port))

    }
  }
}
