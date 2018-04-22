package com.jgibbons.kglance.usersessions

import java.time.LocalDateTime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.jgibbons.kglance.usersessions.UserSessionCacheActor._
import org.scalatest.{BeforeAndAfterAll, FlatSpec, WordSpecLike}

import scala.concurrent.duration._

/**
  * Created by Jonathan during 2018.
  */
class UserSessionCacheActorSpec extends TestKit(ActorSystem("SessionCacheTests")) with ImplicitSender
    with WordSpecLike  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "The UserSessionCacheActor" must {


    "Register a User" in {
      val cache = system.actorOf(UserSessionCacheActor.props(60, true, 60))

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val rep: AnyRef = receiveOne(1000 millis)

      val sessionId = rep match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }

      cache ! ConfirmSessionIdInMsg(sessionId, "127.0.0.1")
      val rep1: AnyRef = receiveOne(1000 millis)
      rep1 match {
        case SessionIdCheckOutMsg(status) if (status==SessionValid) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }

      cache ! ConfirmSessionIdInMsg(sessionId, "255.255.255.1")
      val rep2: AnyRef = receiveOne(1000 millis)
      rep2 match {
        case SessionIdCheckOutMsg(status) if (status==BadIpAddress) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
    }

    "Dont enforce ip address checking" in {
      val cache = system.actorOf(UserSessionCacheActor.props(60, false, 60))

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val rep: AnyRef = receiveOne(1000 millis)

      val sessionId = rep match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }

      cache ! ConfirmSessionIdInMsg(sessionId, "255.255.255.1")
      val rep2: AnyRef = receiveOne(1000 millis)
      rep2 match {
        case SessionIdCheckOutMsg(status) if (status==SessionValid) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
    }

    "Time out old session" in {
      val cache = system.actorOf(UserSessionCacheActor.props(-10, true, 60))

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val rep: AnyRef = receiveOne(1000 millis)

      val sessionId = rep match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }

      cache ! ConfirmSessionIdInMsg(sessionId, "127.0.0.1")
      val rep1: AnyRef = receiveOne(1000 millis)
      rep1 match {
        case SessionIdCheckOutMsg(status) if (status == SessionTimedOut) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
    }

    "Allow many sessions from same host" in {
      val cache = system.actorOf(UserSessionCacheActor.props(100, true, 60))

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val rep: AnyRef = receiveOne(1000 millis)

      val sessionId = rep match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val rep1: AnyRef = receiveOne(1000 millis)

      val sessionId2 = rep1 match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }
      assert(sessionId != sessionId2)
    }

    "Confirm Housekeeping removed timed-out sessoins" in {
      val cache = system.actorOf(UserSessionCacheActor.props(-10, true,60))

      cache ! RegisterUserInMsg("Jonathan", "127.0.0.1")

      val sessionId = receiveOne(1000 millis) match {
        case UserSessionIdOutMsg(sessId) => // cool
          sessId
        case m@_ => fail(s"Did not expect reply ${m}")
      }
      cache ! DoHousekeepingInMsg(LocalDateTime.now().plusMinutes(10))
      receiveOne(1000 millis) match {
        case HousekeepingOutMsg(numSess) if (numSess==1) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
      cache ! DoHousekeepingInMsg(LocalDateTime.now().minusMinutes(10))
      receiveOne(1000 millis) match {
        case HousekeepingOutMsg(numSess) if (numSess==0) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
    }

    "Show no session" in {
      val cache = system.actorOf(UserSessionCacheActor.props(-10, true,60))

      cache ! ConfirmSessionIdInMsg("sessionId", "127.0.0.1")
      val rep1: AnyRef = receiveOne(1000 millis)
      rep1 match {
        case SessionIdCheckOutMsg(status) if (status == NoSessionStored) => // cool
        case m@_ => fail(s"Did not expect reply ${m}")
      }
    }

  }
}
