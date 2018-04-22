package com.jgibbons.kglance.kafkaadmin

import java.time.LocalDateTime
import java.util.Properties

import kafka.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import kafka.coordinator.group.GroupOverview
import org.apache.kafka.clients.admin.KafkaAdminClient
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.{KafkaFuture, PartitionInfo, TopicPartition}
import org.slf4j.LoggerFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.util.matching.Regex

/**
  * Licensed to the Apache Software Foundation (ASF) under one or more
  * contributor license agreements.  See the NOTICE file distributed with
  * this work for additional information regarding copyright ownership.
  * The ASF licenses this file to You under the Apache License, Version 2.0
  * (the "License"); you may not use this file except in compliance with
  * the License.  You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *
  * This code is copied from:
  * https://stackoverflow.com/questions/42201616/how-to-get-kafka-consume-lag-in-java-program
  *
  * also worth looking at:
  * https://github.com/apache/kafka/blob/trunk/core/src/main/scala/kafka/admin/ConsumerGroupCommand.scala
  *
  * This class is not thread safe, so it is wrapped in an actor
  *
  * @param kafkaProperties Come from resources.conf, under kafka{}
  * @param ignoreTopics - you create a regex by .r on a string, eg ".*".r
  * @param monitorTopics - you create a regex by .r on a string, eg ".*".r
  */
class KafkaTopicUtilsImpl(kafkaProperties: Properties, monitorTopics:Set[Regex], ignoreTopics:Set[Regex]=Set.empty[Regex])
  extends KafkaTopicUtils {
  val NO_CONSUMERS_GROUP_ID = ""
  private var adminClient : Option[AdminClient] = None
  private var consumer :Option[KafkaConsumer[_, _]] = None
  private val logger = LoggerFactory.getLogger(KafkaTopicUtilsImpl.getClass)

  /**
    * Confirms the topic matched the monitored list, and is not excluded by the ignore list
    * @param topicName The topic name to check
    * @return true if the topic is to be monitored
    */
  private[kafkaadmin] def isTopicHandled(topicName:String) : Boolean = {
    monitorTopics.exists(_.findFirstIn(topicName).nonEmpty) match {
      case true =>
        ignoreTopics.isEmpty || ignoreTopics.exists(_.findFirstIn(topicName).isEmpty)
      case _=> false
    }
  }

  /**
    * If already open, will close the connections and reopen them, or attempt to
    */
  override def open() = {
    close()
    logger.info("Opening KafkaAdminClient")
    adminClient = Some(AdminClient.create(kafkaProperties))
    logger.info("Opening KafkaConsumer")
    consumer = Some(createNewConsumer())
    isOpen = true
  }

  override def close() = {
    adminClient match {
      case Some(ac) =>
        logger.info("Closing KafkaAdminClient")
        ac.close()
      case None =>
    }
    adminClient = None

    consumer match {
      case Some(ac) =>
        logger.info("Closing Kafka Consumer")
        ac.close()
      case None =>
    }
    consumer = None

    isOpen = false
  }

  override def getKafkaSummaryInfo : Option[Map[String,String]] = {
    val ret = mutable.LinkedHashMap.empty[String, String]

    ret(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)= kafkaProperties.getProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG)
    ret("Include Topics")= monitorTopics.mkString(", ")
    ret("Exclude Topics")= ignoreTopics.mkString(", ")
    // @todo, could start getting cluster info from the kafkaadminclient

    // Map toList gives a list of tuples
    Some(Map(ret.toList: _*))
  }


  /**
    * @return List of GlanceTopicInfo, sorted by topicname and consumer name
    */
  override def getTopicConsumerGroupLag: Option[List[GlanceTopicInfo]] = {
    // Now get the committed offsets
    val nowStr = LocalDateTime.now.toString

    adminClient match {
      case Some(kafkaAdminClient) =>
        val topicToConsumerToPartitionToOffset = getConsumerCommittedInfo(kafkaAdminClient)
        val consumerGroups: Set[String] = topicToConsumerToPartitionToOffset.values.flatMap(_.keySet).toSet
        val groupIdToConsumerCount = getNumberOfConsumers(kafkaAdminClient, consumerGroups)

        // Get the topic partition end offsets at the moment
        consumer match {
          case Some(kafkaConsumer) =>
            val topics = listTopics(kafkaConsumer)
            val tpToEndOffset = mutable.Map.empty[TopicPartition, Long]
            topics.foreach(t => tpToEndOffset ++= getTopicEndoffsets(t, kafkaConsumer))

            val indexedResults = matchConsumerToTopicData(topics, tpToEndOffset, topicToConsumerToPartitionToOffset,
              groupIdToConsumerCount, nowStr)
            val ret = indexedResults.flatMap { case (topicName, consumerMap) =>
              consumerMap.map { case (consumerName, infoList) => infoList }
            }.toList

            Some(ret.sortWith( (l,r)=> (l.topicName+l.consumerName) < (r.topicName+r.consumerName)))
          case None => None
        }
      case None => None
    }
  }

  /**
    * Given all the data from Kafka, reduce it to the glance data
    * @param topics  A list of the topic names, filtered to be the ones we are monitoring
    * @param tpToEndOffset A map of topic partition to end offset (ie the last written)
    * @param topicToConsumerToPartitionToOffset A map of topic to consumer to topicpartition to offset (ie read pos)
    * @param groupIdToConsumerCount A map of consumer name to number of consumers
    * @param nowStr A string indicating the date
    * @return A Map of Topic Name to ConsumerGroup to GlanceTopicInfo
    */
  private def matchConsumerToTopicData(topics:List[String],
                                       tpToEndOffset : mutable.Map[TopicPartition, Long],
                                       topicToConsumerToPartitionToOffset : mutable.Map[String, mutable.Map[String, mutable.Map[TopicPartition, Long]]],
                                       groupIdToConsumerCount : Map[String, Int],
                                       nowStr : String
                                      ) : mutable.Map[String, mutable.Map[String,GlanceTopicInfo]]= {
    // Want to return a map of:
    // Topic, Consumer, GlanceTopicInfo
    val retTopicInfo = mutable.Map.empty[String, mutable.Map[String,GlanceTopicInfo]]

    topics.foreach(topicName =>
      topicToConsumerToPartitionToOffset.get(topicName) match {
        case Some(consumerToPartitionToOffset) =>
          consumerToPartitionToOffset.keySet.foreach(groupId => {
            consumerToPartitionToOffset.get(groupId) match {
              case Some(partitionToOffset) =>
                partitionToOffset.keySet.foreach(tp => {
                  val committed = partitionToOffset(tp)
                  // topicName, groupId, tp,
                  val consumerMap = retTopicInfo.getOrElseUpdate(topicName, mutable.Map.empty[String,GlanceTopicInfo])
                  val glanceInfo = consumerMap.getOrElseUpdate(groupId,
                    GlanceTopicInfo(topicName, groupId,
                      numConsumers = groupIdToConsumerCount.getOrElse(groupId,0 ),
                      dateStr = nowStr))

                  tpToEndOffset.get(tp).filter(_>glanceInfo.endOffset).foreach(glanceInfo.endOffset = _)
                  partitionToOffset.get(tp).filter(_>glanceInfo.commitedOffset).foreach(glanceInfo.commitedOffset = _)
                })
              case None =>
            }
          })
        case None =>
          // Topic with no consumers.
          retTopicInfo(topicName) = mutable.Map.empty[String,GlanceTopicInfo]
          val glanceInfo = GlanceTopicInfo(topicName, NO_CONSUMERS_GROUP_ID, dateStr = nowStr)
          retTopicInfo(topicName)(NO_CONSUMERS_GROUP_ID) = glanceInfo


          tpToEndOffset.keySet.filter(tp=>tp.topic()==topicName).foreach(tp => {
            tpToEndOffset.get(tp).filter(_>glanceInfo.endOffset).foreach(glanceInfo.endOffset = _)
          })
      }
    )
    retTopicInfo
  }

  /**
    * The describe api will describe in detail all the consumers in the consumer group, which is sad
    * as I only want a count of the number of consumers per group.
    * @param client The admin client
    * @param consumerGroups The set of all the groupIds
    * @return A Map of the group name to the count of active consumers in the group
    */
  def getNumberOfConsumers(client:AdminClient, consumerGroups:Set[String]):Map[String, Int] = {
    consumerGroups.map( groupId =>
      (groupId -> getNumberOfConsumersInGroup(client, groupId))
    ).toMap
  }
  def getNumberOfConsumersInGroup(client:AdminClient, consumerGroupId:String): Int = {
    val groupDescription = client.describeConsumerGroup(consumerGroupId)
    val numberOfConsumers = groupDescription.consumers match {
      case Some(consumerList) => consumerList.size
      case None => 0
    }
    numberOfConsumers
  }

  /**
    * Get committed offset for all consumers
    *
    * @return TopicName -> consumerName -> TopicPartition -> commmitPos
    */
  def getConsumerCommittedInfo(client:AdminClient): mutable.Map[String, mutable.Map[String, mutable.Map[TopicPartition, Long]]] = {
    val topicToConsumerToPartitionToOffset = mutable.Map.empty[String, mutable.Map[String, mutable.Map[TopicPartition, Long]]]

    val consumerGroups: List[GroupOverview] = client.listAllConsumerGroupsFlattened
    consumerGroups.foreach(cg => {
      val groupId = cg.groupId

      val offsets: Map[TopicPartition, Long] = client.listGroupOffsets(groupId)
      offsets.keySet.foreach { case tp: TopicPartition if (isTopicHandled(tp.topic())) =>
        val consumerToPartitionToOffset = topicToConsumerToPartitionToOffset.getOrElseUpdate(tp.topic(), mutable.Map.empty[String, mutable.Map[TopicPartition, Long]])
        val partitionToOffset = consumerToPartitionToOffset.getOrElseUpdate(groupId, mutable.Map.empty[TopicPartition, Long])
        //println("### "+tp.topic+"   "+groupId+"  "+tp+" = "+offsets(tp))
        partitionToOffset(tp) = offsets(tp)
      }
    })
    topicToConsumerToPartitionToOffset
  }

  def listTopics(consumer: KafkaConsumer[_, _]): List[String] = {
    // lost topics return Map<String, List<PartitionInfo>>
    val topics = consumer.listTopics()
    topics.keySet().asScala.filter(isTopicHandled(_)).toList
  }

  def createNewConsumer(): KafkaConsumer[_, _] = {
    //    val properties = new Properties()
    //    properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
    //    properties.put(ConsumerConfig.GROUP_ID_CONFIG, "g1")
    //    properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
    //    properties.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000")
    //    properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringDeserializer")
    //    properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.ByteArrayDeserializer")
    //    new KafkaConsumer(properties)
    new KafkaConsumer(kafkaProperties)
  }

  def getTopicEndoffsets(topic: String, consumer: KafkaConsumer[_, _]): mutable.Map[TopicPartition, Long] = {

    val endOffsets = mutable.Map.empty[TopicPartition, Long]

    val partitionInfoList: mutable.Buffer[PartitionInfo] = consumer.partitionsFor(topic).asScala
//    println(partitionInfoList)
    val c = partitionInfoList.map { case pi: PartitionInfo => new TopicPartition(pi.topic(), pi.partition()) }.asJavaCollection
    // Assign replaces any previously manually assigned partitions, and does not cause rebalance
    consumer.assign(c)
    consumer.seekToEnd(c)
    c.forEach(tp => endOffsets.put(tp, consumer.position(tp)))
    endOffsets
  }
}
