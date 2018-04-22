package com.jgibbons.kglance.apigateway

/**
  * Created by Jonathan during 2018.
  */
object SillyCypher {
  val cypher1="abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789+/="
  val cypher2="ctPMRIH3l0QKZTEW9Xxbgah81emfOG5di2jkSV4z6nsv/oqwupyrFUL+DBN7JAYC="

  private def enc(s:String, c1:String, c2:String):String = s.map(c => c1.charAt(c2.indexOf(c))).mkString("")

  def encode(s:String):String = enc(s,cypher2, cypher1)
  def decode(s:String):String = enc(s,cypher1, cypher2)
}

