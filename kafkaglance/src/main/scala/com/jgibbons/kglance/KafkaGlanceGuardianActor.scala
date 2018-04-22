package com.jgibbons.kglance

import akka.actor.SupervisorStrategy.Escalate
import akka.actor.{Actor, ActorLogging, AllForOneStrategy, Props}
import com.jgibbons.kglance.KafkaGlanceGuardianActor.GuardianInitialiseInMsg
import com.jgibbons.kglance.apigateway.UsefulActors
import com.jgibbons.kglance.config.KafkaGlanceConfig
import com.jgibbons.kglance.kafkaadmin.{KafkaInfoActor, KafkaTopicUtilsImpl}
import com.jgibbons.kglance.usersessions.UserSessionCacheActor
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._

/**
  * Created by Jonathan during 2018.
  */
object KafkaGlanceGuardianActor {
  def props(): Props = Props(new KafkaGlanceGuardianActor())

  case object GuardianInitialiseInMsg

}

class KafkaGlanceGuardianActor extends Actor with ActorLogging {

  val systemShutdown = () => {
    systemCloseDown("")
  }

  val reaperActor = context.actorOf(ReaperActor.props(systemShutdown), name = ReaperActor.name)
  // I couldnt get the ApiGateway to run as an actor, seems easiest to leave it at the top level
  // which means, may as well parse the config again here.
  val config = KafkaGlanceConfig(ConfigFactory.load())

  // Included just in case you want to know about lifecycle
  override def preStart = {
    log.info("preStart")
    super.preStart
  }

  override def postStop = {
    log.info("postStop")
    systemCloseDown("")
    super.postStop
  }

  // Included just in case you want to know about supervisor strategy
  override val supervisorStrategy =
    AllForOneStrategy(maxNrOfRetries = 0, withinTimeRange = 1.minute) {
      //      case _: ActorInitializationException => Stop
      //      case _: ActorKilledException => Stop
      //      case _: DeathPactException => Stop
      //      case _: org.apache.kafka.common.config.ConfigException => Stop
      case e: Exception =>
        //        systemCloseDown(e.getClass.getName + " : " + e.getMessage)
        Escalate //Restart
    }

  def receive = {
    case GuardianInitialiseInMsg => sender() ! initialiseTheServer
    case e: AnyRef => log.warning(s"Received an unknown event [${e.getClass.getName}]")
  }

  /**
    * Start up the actors, and return as a bundle of actor refs
    * This is a stupid pattern, but I couldnt get the akkhttp main server
    * to work in an actor after 10 mins so gave up.
    */
  def initialiseTheServer: UsefulActors = {
    val sessionCacheActor = context.actorOf(UserSessionCacheActor.props(config.sessionTimeoutMins, config.sessionApplyIpAddrCheck, 60))

    val kafkaUtils = new KafkaTopicUtilsImpl(config.kafkaProperties, config.monitorTopics, config.ignoreTopics)
    val kafkaUtilsActor = context.actorOf(KafkaInfoActor.props(kafkaUtils, config.resultCacheTimeMs))
    UsefulActors(sessionCacheActor, kafkaUtilsActor)
  }


  protected def closeResources() = {
  }

  def systemCloseDown(msg: String): Unit = {
    // for the future
    import context.dispatcher

    // Add any system wide coordinated close down
    closeResources()

    if (msg.length > 0) log.error(msg)
    log.error("System is terminating.")
    // Terminate like this so that the async logging can complete
    // problem is that it sends a poisonpill will sit at the end of the actor
    // q, so eg if the producer has 100 messges then it will take ages

    context.system.terminate().onComplete {
      case _ => System.exit(1)
    }
  }
}