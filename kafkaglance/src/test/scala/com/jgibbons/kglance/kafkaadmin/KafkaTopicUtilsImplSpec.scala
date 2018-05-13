package com.jgibbons.kglance.kafkaadmin

import java.util.Properties

import org.apache.kafka.common.TopicPartition
import org.scalatest.FlatSpec

import scala.collection.mutable
import scala.util.matching.Regex

/**
  * Created by Jonathan during 2018.
  */
class KafkaTopicUtilsImplSpec extends FlatSpec {
  behavior of "KafkaTopicUtils"

  it should "Use regex to filter topics" in {
    val k = new KafkaTopicUtilsImpl(new Properties(), Set(".*".r), Set.empty[Regex])
    assert(k.isTopicHandled(""))
    assert(k.isTopicHandled("foo"))
    assert(k.isTopicHandled("bar"))
    assert(k.isTopicHandled(".*"))
  }
  it should "Use regex to unfilter topics" in {
    val k = new KafkaTopicUtilsImpl(new Properties(), Set(".*".r), Set("__consumer_offsets".r))
    assert(k.isTopicHandled("foobar"))
    assert(!k.isTopicHandled("__consumer_offsets"))
  }
  it should "Use regex to filter prod topics but not prod_crap" in {
    val k = new KafkaTopicUtilsImpl(new Properties(), Set("prod_.*".r), Set("prod_crap_.*".r))
    assert(!k.isTopicHandled("foobar"))
    assert(!k.isTopicHandled("prod_crap_something"))
    assert(k.isTopicHandled("prod_something"))
  }

  /**
    * Topics T1 and T2, T2 has no condumers, T1 has consumer "Group1" and has one consumer who is at read pos 10.
    * But the topics end pos is 100
    * T2's end pos is at 200, but as I said, no consumers.
    */
  it should "matchConsumerToTopicData" in {
    val k = new KafkaTopicUtilsImpl(new Properties(), Set(".*".r), Set(".*".r))
    val topics = List("t1", "t2")
    val tpToEndOffset = mutable.Map(new TopicPartition("t1",1)->100L,new TopicPartition("t2",2)->200L)
    //  A map of topic to consumer to topicpartition to offset (ie read pos)
    val topicToConsumerToPartitionToOffset =
      mutable.Map("t1"->mutable.Map("Group1"->mutable.Map(new TopicPartition("t1",1)->10L)))

    val groupIdToConsumerCount = Map("Group1"->3)
    val nowStr = "whatever"

    // expect A Map of Topic Name to ConsumerGroup to GlanceTopicInfo
    // mutable.Map[String, mutable.Map[String,GlanceTopicInfo]]= {
    val res =
      k.matchConsumerToTopicData(topics, tpToEndOffset, topicToConsumerToPartitionToOffset, groupIdToConsumerCount, nowStr)

    res.get("t1") match {
      case Some(groupToInfo) =>
        groupToInfo.get("Group1") match {
          case Some(info) =>
            assert(info.consumerName=="Group1")
            assert(info.topicName=="t1")
            assert(info.dateStr==nowStr)
            assert(info.endOffset==100L)
            assert(info.commitedOffset==10L)
            assert(info.lag==90L)
            assert(info.numConsumers==3)
          case None => fail("Expected t1.Group1 to have info")
        }
      case None => fail("Expected topic t1 to have info")
    }
    res.get("t2") match {
      case Some(groupToInfo) =>
        groupToInfo.get("") match {
          case Some(info) =>
            assert(info.consumerName=="")
            assert(info.topicName=="t2")
            assert(info.dateStr==nowStr)
            assert(info.endOffset==200L)
            assert(info.commitedOffset==0L)
            assert(info.lag==200L)
          case None => fail("Expected t2 no group, to have info")
        }
      case None => fail("Expected topic t2 to have info")
    }
  }
  /**
    * Topic T1 has consumer "Group1" and has two consumers who are at read pos 10 and 20
    * But the topics end pos is 100 on partition 1 and 101 on partition 2
    */
  it should "matchConsumerToTopicData and choose highest pos" in {
    val k = new KafkaTopicUtilsImpl(new Properties(), Set(".*".r), Set(".*".r))
    val topics = List("t1")
    val tpToEndOffset = mutable.Map(new TopicPartition("t1",1)->100L,new TopicPartition("t1",2)->101L)
    //  A map of topic to consumer to topicpartition to offset (ie read pos)
    val topicToConsumerToPartitionToOffset =
      mutable.Map("t1"->mutable.Map("Group1"->mutable.Map(new TopicPartition("t1",1)->10L, new TopicPartition("t1",2)->20L)))

    val groupIdToConsumerCount = Map("Group1"->2)
    val nowStr = "whatever"

    // expect A Map of Topic Name to ConsumerGroup to GlanceTopicInfo
    // mutable.Map[String, mutable.Map[String,GlanceTopicInfo]]= {
    val res =
    k.matchConsumerToTopicData(topics, tpToEndOffset, topicToConsumerToPartitionToOffset, groupIdToConsumerCount, nowStr)

    res.get("t1") match {
      case Some(groupToInfo) =>
        groupToInfo.get("Group1") match {
          case Some(info) =>
            assert(info.consumerName=="Group1")
            assert(info.topicName=="t1")
            assert(info.dateStr==nowStr)
            assert(info.endOffset==101L)
            assert(info.commitedOffset==20L)
            assert(info.lag==81L)
            assert(info.numConsumers==2)
          case None => fail("Expected t1.Group1 to have info")
        }
      case None => fail("Expected topic t1 to have info")
    }
  }
}
