package com.jgibbons.kglance.datacache

import com.jgibbons.kglance.kafkaadmin.GlanceTopicInfo

/**
  * Created by Jonathan during 2018.
  *
  * Yes I know about guava.... was bored
  */
class TimeoutCache(val retentionMs:Long) {
  private var kafkaInfo:Option[CachedKafkaInfo] = None
  private var topicInfo:Option[CachedTopicInfo] = None

  def addTopicInfo(data:Option[List[GlanceTopicInfo]]): Option[List[GlanceTopicInfo]] = {
    data match {
      case Some(info) =>topicInfo = Some (new CachedTopicInfo (expiryMs, info) )
      case None => topicInfo = None
    }
    data
  }

  def addKafkaInfo(data:Option[Map[String, String]]): Option[Map[String, String]] = {
    data match {
      case Some(info) => kafkaInfo = Some(new CachedKafkaInfo(expiryMs, info))
      case None => kafkaInfo = None
    }
    data
  }

  def getTopicInfo() : Option[List[GlanceTopicInfo]] = getInfo(topicInfo)
  def getKafkaInfo() : Option[Map[String, String]] = getInfo(kafkaInfo)

  private def getInfo[T](data:Option[CachedData[T]]) : Option[T] = {
    data match {
      case None => None
      case Some(info) =>
        info.isExpired match {
          case true=> kafkaInfo=None
            None
          case false => Some(info.data)
        }
    }
  }


  private def expiryMs = System.currentTimeMillis()+retentionMs
}

abstract class CachedData[T](val timeoutMs:Long, val data:T) {
  def isExpired :Boolean = System.currentTimeMillis()>timeoutMs
}

class CachedTopicInfo(timeoutMs:Long, data:List[GlanceTopicInfo]) extends CachedData(timeoutMs, data)
class CachedKafkaInfo(timeoutMs:Long, data:Map[String, String]) extends CachedData(timeoutMs, data)