package com.github.sessiongen.sessiontracker

import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.time.Time
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

sealed trait RestartStrategy
case class FIXED_DELAY(attempts: Int, delayInMillis: Long) extends RestartStrategy
case class FAILURE_RATE(maxFailures: Int, intervalInMillis: Long, delayInMillis: Long) extends RestartStrategy
case class NO_RESTART() extends RestartStrategy

object RestartStrategy {
  private val separator = ":"
  val FIXED_DELAY_FORMAT: String = s"fixed-delay:attempts=<num>,delay=<ms>"
  val FAILURE_RATE_FORMAT: String = s"failure-rate:failures=<num>,interval=<ms>,delay=<ms>"
  val FORMAT: String = s"(no-restart|$FIXED_DELAY_FORMAT|$FAILURE_RATE_FORMAT)"

  def parse: String => RestartStrategy = {
    case "no-restart" => NO_RESTART()
    case x if x.startsWith("fixed-delay:") =>
      val (_, options) = x.splitAt(x.indexOf(':')+1)
      val map = implicitly[scopt.Read[Map[String, String]]].reads(options)
      FIXED_DELAY(map("attempts").toInt, map("delay").toLong)

    case x if x.startsWith("failure-rate:") =>
      val (_, options) = x.splitAt(x.indexOf(':')+1)
      val map = implicitly[scopt.Read[Map[String, String]]].reads(options)
      FAILURE_RATE(map("failures").toInt, map("interval").toLong, map("delay").toLong)

    case x =>
      throw new IllegalArgumentException("Unknown restart strategy : " + x)
  }

  implicit val read: scopt.Read[RestartStrategy] = scopt.Read.reads(parse)

  def setupEnvironment(env: StreamExecutionEnvironment, config: Config): Unit = {
    import config._

    env.setRestartStrategy(
      restartStrategy match {
        case NO_RESTART() =>
          RestartStrategies.noRestart()

        case FIXED_DELAY(attempts, delayInMillis) =>
          RestartStrategies.fixedDelayRestart(
            attempts,
            Time.milliseconds(delayInMillis)
          )
        case FAILURE_RATE(maxFailures, intervalInMillis, delayInMillis) =>
          RestartStrategies.failureRateRestart(
            maxFailures,
            Time.milliseconds(intervalInMillis), Time.milliseconds(delayInMillis)
          )
      }
    )
  }
}