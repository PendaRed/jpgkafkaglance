package com.jgibbons.kglance.apigateway

import akka.actor.ActorRef

/**
  * Created by Jonathan during 2018.
  */
case class UsefulActors(userSessionCache:ActorRef,
                        kafkaUtilsActor:ActorRef) {
}
