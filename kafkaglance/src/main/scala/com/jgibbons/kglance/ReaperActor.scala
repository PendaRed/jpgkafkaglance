package com.jgibbons.kglance

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Jonathan during 2018.
  *
  * Based on https://gist.github.com/lazyvalue/4432284, but changed
  */
trait ReaperWatched { this: Actor with ActorLogging =>
  override def preStart() {
    log.info(s"[${self.path}] Registering with the Reaper")
    context.actorSelection("/user/" + ReaperActor.name) ! ReaperActor.WatchMeInMsg(self)
  }
}

object ReaperActor {
  val name="ReaperActor"

  def props(additionalShutdown: () => Unit) = Props(new ReaperActor(additionalShutdown))

  case class WatchMeInMsg(ref: ActorRef)
}

class ReaperActor(additionalShutdown: () => Unit) extends Actor with ActorLogging{
  import ReaperActor._

  // Keep track of what we're watching
  val watched = ArrayBuffer.empty[ActorRef]

  // Derivations need to implement this method.  It's the
  // hook that's called when everything's dead
  def allSoulsReaped() = {
    log.info("SYSTEM SHUTDOWN START")
    additionalShutdown()
    context.system.terminate()
    log.info("SYSTEM SHUTDOWN END")
  }

  // Watch and check for termination
  final def receive = {
    case WatchMeInMsg(ref) =>
      context.watch(ref)
      watched += ref
    case Terminated(ref) =>
      watched -= ref
      if (watched.isEmpty) allSoulsReaped()
  }
}
