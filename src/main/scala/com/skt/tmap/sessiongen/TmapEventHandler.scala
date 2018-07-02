package com.skt.tmap.sessiongen

import java.text.SimpleDateFormat
import java.util.Date

import com.github.sessiongen.{Event, Generator}

class TmapEventHandler extends Generator.EventHandler {
  val dateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
  val dateFormatInMillis = new SimpleDateFormat("yyyyMMddHHmmssSSS")

  override def handle(event: Event): String = {
    import event._
    val dateString = dateFormat.format(new Date(timestamp))
    val eventCode = if (logout) 5 else 4
    val sessionId = "...."+dateFormatInMillis.format(new Date(timestamp))+"..."
    s"""{"userKey": "$id", "sessionSeq": "0", "reqTime": "$dateString", "eventCode": "$eventCode", "sessionId": "$sessionId", "payload": "$payload"}"""
  }
}
