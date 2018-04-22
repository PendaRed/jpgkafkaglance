package com.jgibbons.kglance.apigateway

import org.scalatest.FlatSpec

/**
  * Created by Jonathan during 2018.
  */
class SillyCypherSpec extends FlatSpec {
  behavior of "SillyCypher"

  it should "Encode and decode" in {
    val ip = "Z2liYm9uc19qb25hdGhhbkBob3RtYWlsLmNvbTpBbHRyaWFj"
    val enc = SillyCypher.encode(ip)
    assert(ip!=enc)
    val dec = SillyCypher.decode(enc)
    assert(ip==dec)
  }
}
