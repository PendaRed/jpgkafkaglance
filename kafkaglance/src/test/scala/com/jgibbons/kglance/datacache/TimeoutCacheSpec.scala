package com.jgibbons.kglance.datacache

import com.jgibbons.kglance.kafkaadmin.GlanceTopicInfo
import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2018.
  */
class TimeoutCacheSpec extends FlatSpec {
  behavior of "TimeoutCache"

  it should "Handle no data" in {
    val v = new TimeoutCache(10)
    assert(v.getKafkaInfo().isEmpty)
    assert(v.getTopicInfo().isEmpty)
  }
  it should "Cache data" in {
    val v = new TimeoutCache(100)
    v.addKafkaInfo(Some(Map("A"->"b", "C"->"d")))
    v.addTopicInfo(Some(List(GlanceTopicInfo("topic","consumer"))))
    assert(v.getKafkaInfo().isDefined)
    assert(v.getTopicInfo().isDefined)
  }
  it should "Add None evicts" in {
    val v = new TimeoutCache(100)
    v.addKafkaInfo(Some(Map("A"->"b", "C"->"d")))
    v.addTopicInfo(Some(List(GlanceTopicInfo("topic","consumer"))))
    assert(v.getKafkaInfo().isDefined)
    assert(v.getTopicInfo().isDefined)
    v.addKafkaInfo(None)
    v.addTopicInfo(None)
    assert(v.getKafkaInfo().isEmpty)
    assert(v.getTopicInfo().isEmpty)
  }
  it should "Timeout data" in {
    val v = new TimeoutCache(0)
    v.addKafkaInfo(Some(Map("A"->"b", "C"->"d")))
    v.addTopicInfo(Some(List(GlanceTopicInfo("topic","consumer"))))
    Thread.sleep(2)
    assert(v.getKafkaInfo().isEmpty)
    assert(v.getTopicInfo().isEmpty)
  }

}
