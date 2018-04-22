package com.jgibbons.kglance.kafkaadmin

/**
  * Created by Jonathan during 2018.
  *
  * This is the json reply from a POST to auth.html
  * It is sent back to the login.html which compares isOk=="OK"
  * and if good then uses the cookieVal and redirects to kafkaglance.html
  */
case class GlanceLoginInfo(isOk:String, cookieVal:String)
