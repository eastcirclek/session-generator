package com.github.sessiongen

import com.github.sessiongen.Generator.EventHandler
import com.typesafe.scalalogging.LazyLogging
import java.util.Properties
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import scala.util.Random

class Generator(config: Config, name: String) extends LazyLogging {
  import config._

  private val eventHandler: EventHandler = Generator.loadEventHandler(eventHandlerOpt, eventHandlerArgsOpt)
  private val timeSlots: Int = rate * interval
  private val usersPerSlot: Int = adjustedTotalUsers / timeSlots
  private val intervalInNanos: Long = (1000000000L / rate.toFloat).toLong
  private val payload: String = Random.alphanumeric.take(payloadSize).mkString
  private val table: Array[TimeSlot] = Array.tabulate(timeSlots) { i =>
    val first = i*usersPerSlot
    val last = first+usersPerSlot-1
    new TimeSlot(first, last, logoutProbability)
  }

  private def time[R](block: => R): (R, Long) = {
    val t0 = System.nanoTime()
    val result = block    // call-by-name
    val t1 = System.nanoTime()
    (result, t1-t0)
  }

  def run(feeder: (String, String, Long) => Boolean): Unit = {
    val last = runningTime.toLong * rate
    var count = 0L
    var loopCount = 0L
    var running = true
    while (running && (last - count > 0)) {
      val remaining = math.min(timeSlots, last-count)

      val (_, iterDurationNanos) = time {
        table.take(remaining.toInt) foreach { entry =>
          val (done, nanos) = time {
            val timestamp = System.currentTimeMillis
            val (seq, logout) = entry.getUserIdAndWhetherLoggedOut()
            val id = s"$name$seq"
            val json = eventHandler.handle(Event(id, logout, timestamp, payload))
            feeder(id, json, timestamp)
          }

          if (done) {
            running = false
          } else {
            val diff = intervalInNanos - nanos
            if (diff > 0) {
              val start = System.nanoTime
              var end = 0L
              do {
                end = System.nanoTime
              } while(start + diff >= end) // spin lock
            }
          }
          count += 1
        }
        loopCount += 1
      }

      logger.info(s"$loopCount-th iteration for $remaining items : $iterDurationNanos ns")
    }

    (0 until timeSlots) foreach { i =>
      table(i).logout()
      logger.debug(s"$i ${table(i).history.reverse}")
    }
  }
}

object Generator {
  def loadEventHandler(eventHandlerOpt: Option[String], eventHandlerArgsOpt: Option[String]): EventHandler = {
    def createObject[T <: AnyRef](className: String, args: AnyRef*): T = {
      val klass = Class.forName(className).asInstanceOf[Class[T]]
      val constructor = klass.getConstructor(args.map(_.getClass): _*)
      constructor.newInstance(args: _*)
    }

    (eventHandlerOpt, eventHandlerArgsOpt) match {
      case (Some(eventHandler), None) =>
        createObject[EventHandler](eventHandler)

      case (Some(eventHandler), Some(eventHandlerArgs)) =>
        createObject[EventHandler](eventHandler, eventHandlerArgs)

      case (None, _) =>
        DefaultEventHandler
    }
  }

  def main(args : Array[String]) {
    val programDescription = "A user session generator based on periodic user events"
    val config = Config.get(args, programDescription)
    import config._

    val generator = new Generator(config, keyPrefix)

    outputMode match {
      case Kafka() =>
        val producer: KafkaProducer[String, String] = {
          val props = new Properties()
          props.put("bootstrap.servers", bootstrapServers.mkString(","))
          props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
          props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
          new KafkaProducer(props)
        }
        def kafkaFeeder(id: String, jsonString: String, timestamp: Long): Boolean = {
          val rec = new ProducerRecord[String, String](topic, id, jsonString)
          producer.send(rec)
          false
        }
        generator.run(kafkaFeeder)
        producer.close()

      case Stdout() =>
        def stdoutFeeder(id: String, jsonString: String, timestamp: Long): Boolean = {
          println(jsonString)
          false
        }
        generator.run(stdoutFeeder)
    }
  }

  trait EventHandler {
    def handle(event: Event): String
  }

  private[sessiongen] object DefaultEventHandler extends EventHandler {
    override def handle(event: Event): String = {
      import event._
      s"""{"id": "$id", "logout": "$logout", "time": "$timestamp", "payload": "$payload"}"""
    }
  }
}
