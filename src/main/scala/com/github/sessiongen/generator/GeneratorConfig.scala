package com.github.sessiongen.generator

import java.io.FileInputStream
import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

case class GeneratorConfig(rate: Int = -1,
                           interval: Int = -1,
//                           totalUsers: Int = -1,
                           logoutProbability: Double = Double.NaN,
                           payloadSize: Int = 10,
                           outputMode: OutputMode = Stdout(),
                           producerProperty: Properties = new Properties(),
                           topic: String = "",
                           keyPrefix: String = "",
                           runningTime: Int = Int.MaxValue,
                           eventHandlerOpt: Option[String] = None,
                           eventHandlerArgsOpt: Option[String] = None) {
//  def adjustedTotalUsers = {
//    val h = rate * interval
//    val rem = totalUsers % h
//    totalUsers - rem
//  }
  def totalUsers = rate * interval
}

object GeneratorConfig extends LazyLogging {

  implicit val propertiesRead: scopt.Read[Properties] =
    scopt.Read.reads { x =>
      val properties = new Properties()
      properties.load(new FileInputStream(x))
      properties
    }

  def getParser(args: Array[String], programName: String): OptionParser[GeneratorConfig] = {
    new OptionParser[GeneratorConfig](programName) {
      head(programName)

      help("help").text("prints this usage text")

      opt[Int]("rate")
        .required()
        .action((x, c) => c.copy(rate = x))
        .validate(x => if (x > 0) success else failure(s"rate must be positive but $x"))
        .valueName("<num>")
        .text("events per second")

      opt[Int]("interval")
        .required()
        .action((x, c) => c.copy(interval = x))
        .validate(x => if (x > 0) success else failure(s"interval must be positive but $x"))
        .valueName("<sec>")
        .text("interval between events of each user")

//      opt[Int]("total")
//        .required()
//        .action((x, c) => c.copy(totalUsers = x))
//        .validate(x => if (x > 0) success else failure(s"total must be positive but $x"))
//        .valueName("<num>")
//        .text("total number of users")

      opt[Double]("logout-probability")
        .required()
        .action((x, c) => c.copy(logoutProbability = x))
        .validate(x =>
          if (x >= 0.0 && x <= 1.0)
            success
          else
            failure(s"logout-probability must be between 0.0 and 1.0 but $x")
        )
        .valueName("<fraction>")
        .text("probability of a user's logging out after emitting an event")

      opt[Int]("payload-size")
        .action((x, c) => c.copy(payloadSize = x))
        .validate(x => if (x > 0) success else failure(s"payloadSize must be positive but $x"))
        .valueName("<bytes>")
        .text("payload size of each event")

      opt[Int]("running-time")
        .action((x, c) => c.copy(runningTime = x))
        .validate(x => if (x > 0) success else failure(s"running-time must be positive but $x"))
        .valueName("<sec>")
        .text("running time of this generator")

      opt[String]("output")
        .action { (x, c) =>
          val outputMode = x.toLowerCase match {
            case "stdout" => Stdout()
            case "kafka" => Kafka()
          }
          c.copy(outputMode = outputMode)
        }
        .validate { x =>
          x.toLowerCase match {
            case "stdout" | "kafka" => success
            case _ => failure("supported output : stdout or kafka")
          }
        }
        .valueName("<stdout | kafka>")
        .text("where to generate user events")

      opt[Properties]("producer-property")
        .action((x, c) => c.copy(producerProperty = x))
        .text("property file for Kafka producer")

      opt[String]("topic")
        .action((x, c) => c.copy(topic = x))
        .text("valid only for Kafka output mode")

      opt[String]("prefix")
        .action((x, c) => c.copy(keyPrefix = x))
        .text("prefix to default numeric ids of users")

      opt[String]("event-handler")
        .action((x, c) => c.copy(eventHandlerOpt = Some(x)))
        .text("event handler applied to every user event")

      opt[String]("event-handler-args")
        .action((x, c) => c.copy(eventHandlerArgsOpt = Some(x)))
        .text("arguments passed to event handler constructor")

      checkConfig { c =>
        val entries = c.rate.toLong * c.interval
        if (entries > Int.MaxValue) {
          failure(s"<rate>*<interval> cannot exceed ${Int.MaxValue}, " +
            s"but ${c.rate}*${c.interval} is ${entries}")
        } else if (c.totalUsers < entries) {
          failure(s"<total> cannot be smaller than <rate>*<interval>, " +
            s"but ${c.totalUsers} <= ${c.rate} * ${c.interval}")
//        } else if (c.adjustedTotalUsers != c.totalUsers ) {
//          reportWarning(s"<total> is adjusted from ${c.totalUsers} to ${c.adjustedTotalUsers}")
//          success
        } else {
          success
        }
      }
    }
  }

  def get(args: Array[String], programName: String): GeneratorConfig = {
    val parser = getParser(args, programName)

    parser.parse(args, GeneratorConfig()) match {
      case Some(c) => c
      case None => throw new RuntimeException("Failed to get a valid configuration object")
    }
  }
}
