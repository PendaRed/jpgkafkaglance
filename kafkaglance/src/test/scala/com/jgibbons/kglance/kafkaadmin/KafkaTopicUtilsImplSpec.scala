package com.jgibbons.kglance.kafkaadmin

import java.util.Properties

import org.scalatest.FlatSpec

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

}
