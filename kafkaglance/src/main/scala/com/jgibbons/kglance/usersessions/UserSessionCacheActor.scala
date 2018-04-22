package com.jgibbons.kglance.usersessions

import java.time.{LocalDateTime, Period}
import java.time.temporal.{ChronoUnit, TemporalUnit}
import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props}
import com.jgibbons.kglance.usersessions.UserSessionCacheActor._

import scala.collection.mutable


/**
  * Created by Jonathan during 2018.
  */
object UserSessionCacheActor {
  def props(maxSessionAgeMins:Int, applyIpAddrCheck:Boolean, housekeepingPeriodSecs:Int): Props = Props(new UserSessionCacheActor(maxSessionAgeMins, applyIpAddrCheck,housekeepingPeriodSecs))

  sealed trait UserSessionStatus
  object SessionTimedOut extends UserSessionStatus
  object BadIpAddress extends UserSessionStatus
  object NoSessionStored extends UserSessionStatus
  object SessionValid extends UserSessionStatus

  case class RegisterUserInMsg(userName:String, remoteIp:String)
  case class UserSessionIdOutMsg(sessionId:String)
  case class ConfirmSessionIdInMsg(sessionId:String, remoteIp:String)
  case class SessionIdCheckOutMsg(valid:UserSessionStatus)
  case class DoHousekeepingInMsg(sessionsTimeoutAfter:LocalDateTime)
  case class HousekeepingOutMsg(numberOfSessionsLeft:Int)
}

class UserSessionCacheActor(val maxSessionAgeMins:Int,
                            val applyIpAddrCheck:Boolean,
                            val housekeepingPeriodSecs:Int) extends Actor with ActorLogging {
  private val sessionIdLookup = mutable.Map.empty[String, Tuple3[String, String, LocalDateTime]]
  private val userIpLookup = mutable.Map.empty[String, String]
  private var nextHousekeep = LocalDateTime.now().plusSeconds(housekeepingPeriodSecs)

  override def receive = {
    case RegisterUserInMsg(user, remoteIpAddr) =>
      val sessionId = createNewSessionId(user, remoteIpAddr)
      sender() ! UserSessionIdOutMsg(sessionId)
    case ConfirmSessionIdInMsg(sessionId, remoteIpAddr) =>
      sender() ! SessionIdCheckOutMsg(confirmSessionId(sessionId, remoteIpAddr))
    case DoHousekeepingInMsg(sessionsTimeoutAfter) =>
      houseKeeping(sessionsTimeoutAfter)
      sender() ! HousekeepingOutMsg(sessionIdLookup.size)
  }

  private def removeTimedOutSessions()={
    val removeSet = mutable.ArrayBuffer.empty[Tuple2[String, String]]
    userIpLookup.keySet.foreach(key=> {
      userIpLookup.get(key) match {
        case Some(sessionId) =>
          sessionIdLookup.get(sessionId) match {
            case Some(userData) if (isSessionOld(userData._3)) => {
              log.info(s"[$sessionId] Housekeeping removing old user session [$userData]")
              // unsafe to remove within an iteration.  Should filter and then -=, or do this
              removeSet+= Tuple2(sessionId, key)
            }
            case _ =>
          }
        case _ =>
      }
    })
    removeSet.foreach(e => {
      sessionIdLookup.remove(e._1)
      userIpLookup.remove(e._2)
    })

    log.debug(s"Housekeeping left [${userIpLookup.size}] active sessions in the cache.")
  }

  private[usersessions] def houseKeeping(scheduleAfter:LocalDateTime) = {
    if (LocalDateTime.now().isAfter(scheduleAfter)) {
      removeTimedOutSessions()
      nextHousekeep = LocalDateTime.now().plusSeconds(housekeepingPeriodSecs)
    }
  }

  private def getKey(user:String, remoteIpAddr:String) = user+"_"+remoteIpAddr


  private[usersessions] def createNewSessionId(user: String, remoteIpAddr: String) = {
    val key = getKey(user, remoteIpAddr)

    val startTime = LocalDateTime.now()

    val sessionId = UUID.randomUUID().toString
    log.debug(s"[$sessionId] Adding to session cache, as user [$user] called createNewSesion from [$remoteIpAddr]")
    sessionIdLookup(sessionId) = (user, remoteIpAddr, startTime)
    userIpLookup(key) = sessionId
    sessionId
  }

  private[usersessions] def confirmSessionId(sessionId:String, remoteIpAddr:String): UserSessionStatus = {
    houseKeeping(nextHousekeep)
    sessionIdLookup.get(sessionId) match {
      case Some((user, prevIpAddr, startTime)) if (applyIpAddrCheck && prevIpAddr != remoteIpAddr)  =>
        log.debug(s"[$sessionId] for user [$user] on [$prevIpAddr] doesn't match new IPAddress [$remoteIpAddr]")
        BadIpAddress
      case Some((user, prevIpAddr, startTime)) if (isSessionOld(startTime))  =>
        log.debug(s"[$sessionId] for user [$user] on [$prevIpAddr] session timeout - session last used at [$startTime]")
        SessionTimedOut
      case Some((user, prevIpAddr, startTime)) =>
        val lastUsed = LocalDateTime.now()
        log.debug(s"[$sessionId] for user [$user] on [$prevIpAddr] valid, resetting last used timestamp to [$lastUsed]")
        sessionIdLookup(sessionId) = (user, remoteIpAddr, lastUsed)
        SessionValid
      case None => NoSessionStored
    }
  }

  private[usersessions] def isSessionOld(startTime:LocalDateTime):Boolean = {
    val oldestStartTime = LocalDateTime.now().minus(maxSessionAgeMins, ChronoUnit.MINUTES)
    oldestStartTime.isAfter(startTime)
  }
}
