package com.jgibbons.kglance

import com.jgibbons.kglance.apigateway.ApiGateway

/**
  * Created by Jonathan during 2018.
  */
object KafkaGlance extends App {
  val god = new ApiGateway()

  // Lets any console errors appear if there are config problems etc
  Thread.sleep(10 * 1000)
}
