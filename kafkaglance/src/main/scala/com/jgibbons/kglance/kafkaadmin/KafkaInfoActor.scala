package com.jgibbons.kglance.kafkaadmin

import java.util.Properties

import akka.actor.{Actor, ActorLogging, Props}
import com.jgibbons.kglance.datacache.TimeoutCache
import com.jgibbons.kglance.kafkaadmin.KafkaInfoActor.{GetKafkaInfoInMsg, GetLatestStatsInMsg, KafkaInfoOutMsg, LatestTopicInfoOutMsg}

import scala.collection.mutable

/**
  * Created by Jonathan during 2018.
  */
object KafkaInfoActor {
  def props(kafkaUtils:KafkaTopicUtils, resultCacheTimeMs:Int) = Props(new KafkaInfoActor(kafkaUtils, resultCacheTimeMs))

  case object GetLatestStatsInMsg
  case class LatestTopicInfoOutMsg(payload:Option[List[GlanceTopicInfo]])

  case object GetKafkaInfoInMsg
  case class KafkaInfoOutMsg(payload:Option[Map[String, String]])
}



class KafkaInfoActor(kafkaUtils:KafkaTopicUtils, resultCacheTimeMs:Int) extends Actor with ActorLogging {
  val dataCache = new TimeoutCache(resultCacheTimeMs)

  override def receive = {
    case GetLatestStatsInMsg =>
      sender ! LatestTopicInfoOutMsg(getLatestTopicInfo())
    case GetKafkaInfoInMsg =>
      sender ! KafkaInfoOutMsg(getKafkaInfo())
  }

  private def getKafkaInfo() : Option[Map[String, String]] = {
    if (!kafkaUtils.isOpen) kafkaUtils.open()
      try {
      kafkaUtils.getKafkaSummaryInfo()
    } catch {
      case e:Exception=>
      log.error("Failed to request latest Kafka Info", e)
      kafkaUtils.close()
      None
    }
  }


  private def getLatestTopicInfo() :Option[List[GlanceTopicInfo]]= {
    dataCache.getTopicInfo() match {
      case r @ Some(cachedInfo) => r
      case None =>
        if (!kafkaUtils.isOpen) kafkaUtils.open()
        try {
          dataCache.addTopicInfo(kafkaUtils.getTopicConsumerGroupLag)
        } catch {
          case e:Exception=>
            log.error("Failed to request latest stats", e)
            kafkaUtils.close()
            None
        }
    }
  }
}