package com.jgibbons.kglance.kafkaadmin

import scala.collection.mutable

/**
  * Created by Jonathan during 2018.
  */
trait KafkaTopicUtils {
  var isOpen = false

  def open()
  def close()

  /**
    * @return Map of Map of Map, where keys are topic->consumerId->GlanceTopicInfo
    */
  def getTopicConsumerGroupLag: Option[List[GlanceTopicInfo]]

  /**
    * @return A Map of key,values which can be shown in the UI
    */
  def getKafkaSummaryInfo() : Option[Map[String,String]]

}
