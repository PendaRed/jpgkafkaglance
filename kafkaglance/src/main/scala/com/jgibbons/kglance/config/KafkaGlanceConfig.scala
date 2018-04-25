package com.jgibbons.kglance.config

import java.util.Map.Entry
import java.util.Properties

import com.typesafe.config.{Config, ConfigValue}
import org.slf4j.LoggerFactory

import scala.collection.immutable.HashSet
import collection.JavaConverters._
import scala.util.matching.Regex

/**
  * Created by Jonathan during 2018.
  */
case class KafkaGlanceConfig(config: Config) {
  private val logger = LoggerFactory.getLogger(this.getClass.getName)

  val envName:String = config.getString("kafkaglance.webserver.envName")
  val hostname:String = config.getString("kafkaglance.webserver.hostName")
  val portNum:Int = config.getInt("kafkaglance.webserver.portNumber")

  val username:String = config.getString("kafkaglance.webserver.username")
  val password:String = config.getString("kafkaglance.webserver.password")

  val sessionTimeoutMins:Int = config.getInt("kafkaglance.webserver.sessionTimeoutMins")
  val sessionApplyIpAddrCheck:Boolean = config.getBoolean("kafkaglance.webserver.sessionApplyIpAddrCheckTrueFalse")

  val resultCacheTimeMs:Int= config.getInt("kafkaglance.resultCacheTimeMs")

  val kafkaProperties:Properties = extractKafkaProperties(config.getConfig("kafka"))

  // hocon returns a java list, so .asScala, then .r makes a regex, so _ is a string, so _.r
  val ignoreTopics:Set[Regex] = config.getStringList("kafkaglance.topics.ignored").asScala.map(_.r).toSet
  val monitorTopics:Set[Regex] = config.getStringList("kafkaglance.topics.monitored").asScala.map(_.r).toSet

  def dumpConfig(prefix: String): String = {
    s"${prefix}kafkaglance.webserver.hostName=[$hostname]" +
      s"${prefix}kafkaglance.webserver.portNumber=[$portNum]"+
      s"${prefix}kafkaglance.webserver.username=$username" +
      s"${prefix}kafkaglance.webserver.password=********" +
      s"${prefix}kafkaglance.webserver.sessionTimeoutMins=$sessionTimeoutMins"+
      s"${prefix}kafkaglance.webserver.sessionApplyIpAddrCheckTrueFalse=$sessionApplyIpAddrCheck" +
      s"${prefix}kafkaglance.resultCacheTimeMs=$resultCacheTimeMs"
      s"${prefix}kafkaglance.topics.ignored=${ignoreTopics.toString}" +
      s"${prefix}kafkaglance.topics.monitored=${monitorTopics.toString}" +
      s"kafka{\n$kafkaProperties\n}"
  }

  def extractKafkaProperties(config:Config) : Properties = {
    val p = new Properties()
    config.entrySet().forEach{ case e:Entry[String,ConfigValue] =>
      p.put(e.getKey, config.getString(e.getKey))
    }
    p
  }
}

