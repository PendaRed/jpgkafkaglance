package com.jgibbons.kglance.kafkaadmin


import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._

/**
  * Created by Jonathan during 2018.
  *
  * collect your json format instances into a support trait:
  */
trait GlanceJsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val topicInfoFormat = jsonFormat6(GlanceTopicInfo)
  implicit val listTopicInfoFormat = jsonFormat3(GlanceNamedList)
  implicit val mapKafkaInfoFormat = jsonFormat3(GlanceNamedMap)

  implicit val glanceLoginInfoFormat = jsonFormat2(GlanceLoginInfo)
}

