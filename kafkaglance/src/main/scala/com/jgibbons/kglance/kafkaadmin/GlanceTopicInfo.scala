package com.jgibbons.kglance.kafkaadmin

/**
  * Created by Jonathan during 2018.
  */
case class GlanceTopicInfo(topicName:String,
                           consumerName:String,
                           var numConsumers: Int=0,
                           var commitedOffset: Long=0,
                           var endOffset: Long=0,
                           var dateStr: String="") {
  def lag :Long = endOffset - commitedOffset
}

/**
  * @param name Not used, just for json support
  * @param forceLogin If "Y" then kglance.js will relocate to the login page
  * @param data If forceLogin != "Y" then holds the topic data
  */
case class GlanceNamedList(name:String, forceLogin:String, data:List[GlanceTopicInfo])

case class GlanceNamedMap(name:String,  forceLogin:String, data:Map[String, String])
