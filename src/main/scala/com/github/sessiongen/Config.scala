package com.github.sessiongen

import java.io.File
import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

case class Config(rate: Int = -1,
                  interval: Int = -1,
                  totalUsers: Int = -1,
                  logoutProbability: Double = Double.NaN,
                  payloadSize: Int = -1,
                  outputMode: OutputMode = Stdout(),
                  propertyFileOpt: Option[File] = None,
                  topic: String = "",
                  keyPrefix: String = "",
                  runningTime: Int = -1,
                  eventHandlerOpt: Option[String] = None,
                  eventHandlerArgsOpt: Option[String] = None) {
  def adjustedTotalUsers = {
    val h = rate * interval
    val rem = totalUsers % h
    totalUsers - rem
  }
}

object Config extends LazyLogging {
  def getParser(args: Array[String], programName: String): OptionParser[Config] = {
    new OptionParser[Config](classOf[Generator].getName) {
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

      opt[Int]("total")
        .required()
        .action((x, c) => c.copy(totalUsers = x))
        .validate(x => if (x > 0) success else failure(s"total must be positive but $x"))
        .valueName("<num>")
        .text("total number of users")

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
        .required()
        .action((x, c) => c.copy(payloadSize = x))
        .validate(x => if (x > 0) success else failure(s"payloadSize must be positive but $x"))
        .valueName("<bytes>")
        .text("payload size of each event")

      opt[Int]("running-time")
        .required()
        .action((x, c) => c.copy(runningTime = x))
        .validate(x => if (x > 0) success else failure(s"running-time must be positive but $x"))
        .valueName("<sec>")
        .text("running time of this generator")

      opt[String]("output")
        .required()
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
            case _ => failure("output-to must be either stdout or kafka.")
          }
        }
        .valueName("<stdout | kafka>")
        .text("where to generate user events")

      opt[File]("producer-property-file")
        .action((x, c) => c.copy(propertyFileOpt = Some(x)))
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
        } else if (c.adjustedTotalUsers != c.totalUsers ) {
          reportWarning(s"<total> is adjusted from ${c.totalUsers} to ${c.adjustedTotalUsers}")
          success
        } else {
          success
        }
      }

      checkConfig { c =>
        c.propertyFileOpt match {
          case Some(x) => success
          case None => failure("Kafka mode requires --propertyFile")
        }
      }
    }
  }

  def get(args: Array[String], programName: String): Config = {
    val parser = getParser(args, programName)

    parser.parse(args, Config()) match {
      case Some(c) => c
      case None => throw new RuntimeException("Failed to get a valid configuration object")
    }
  }
}
