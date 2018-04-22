package com.jgibbons.kglance.kafkaadmin

import com.jgibbons.kglance.config.KafkaGlanceConfig
import com.typesafe.config.ConfigFactory

/**
  * Created by Jonathan during 2018.
  *
  * Sorry this is not really a test, more a hack when I was trying out some stuff on my laptop.
  * Still useful tho - when the adminclient api changes
  */
object KafkaTopicUtilsTester extends App {
  val config = KafkaGlanceConfig(ConfigFactory.load())
  val k = new KafkaTopicUtilsImpl(config.kafkaProperties, Set("lappie_.*".r), Set("__consumer_offsets".r))
  k.open
  for (i<- 1 to 1000) {
    k.getTopicConsumerGroupLag match {
      case Some(lagInfo) =>
        val lines: Seq[String] = lagInfo.map{ case info:GlanceTopicInfo =>
            f"${info.topicName}    ${info.consumerName}   ${info.numConsumers} ${info.commitedOffset} ${info.endOffset} ${info.lag} ${info.dateStr}"
        }.toSeq.sorted
        println("====\n" + lines.mkString("\n"))
      case None =>
        println("Problem connecting")
        k.close
        k.open
    }
    Thread.sleep(1000)
  }
  k.close
}
