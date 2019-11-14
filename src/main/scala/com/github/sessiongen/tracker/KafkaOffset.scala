package com.github.sessiongen.sessiontracker

import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer
import org.apache.flink.streaming.connectors.kafka.internals.KafkaTopicPartition

import scala.collection.JavaConverters._

sealed trait KafkaOffset

case class EARLIEST() extends KafkaOffset
case class LATEST() extends KafkaOffset
case class GROUP() extends KafkaOffset
case class SPECIFIC(offsets: Map[Int, Long]) extends KafkaOffset
case class TIMESTAMP(start: Long, end: Long) extends KafkaOffset

object KafkaOffset {
  val EARLIEST_NAME = "earliest"
  val LATEST_NAME = "latest"
  val GROUP_NAME = "group"
  val SPECIFIC_NAME = "specific"
  val SPECIFIC_FORMAT = SPECIFIC_NAME + ":" + "<partition=offset>,<partition=offset>..."
  val TIMESTAMP_NAME = "timestamp"
  val TIMESTAMP_FORMAT = TIMESTAMP_NAME + ":" + "<timestamp>"

  val FORMAT: String = s"($EARLIEST_NAME|$LATEST_NAME|$GROUP_NAME|$SPECIFIC_FORMAT|$TIMESTAMP_FORMAT)"

  def parse: String => KafkaOffset = {
    case EARLIEST_NAME => EARLIEST()
    case LATEST_NAME => LATEST()
    case GROUP_NAME => GROUP()
    case x if x.startsWith(SPECIFIC_NAME + ":") =>
      val (_, offsets) = x.splitAt(x.indexOf(':')+1)
      SPECIFIC(implicitly[scopt.Read[Map[Int, Long]]].reads(offsets))
    case x if x.startsWith(TIMESTAMP_NAME + ":") =>
      val (_, offsets) = x.splitAt(x.indexOf(':')+1)
      implicitly[scopt.Read[Seq[Long]]].reads(offsets) match {
        case Seq(start) => TIMESTAMP(start, -1L)
        case Seq(start, end) =>
          if (start > end) {
            throw new IllegalArgumentException(s"start($start) cannot proceed end($end)")
          }
          TIMESTAMP(start, end)
        case _ =>
          throw new IllegalArgumentException(s"$TIMESTAMP_NAME requires $TIMESTAMP_FORMAT, but $x entered.")
      }
    case x =>
      throw new IllegalArgumentException("Unknown startup mode : " + x)
  }

  implicit val read: scopt.Read[KafkaOffset] = scopt.Read.reads(parse)

  def setupConsumer(consumer: FlinkKafkaConsumer[_], config: TrackerConfig): Unit = {
    config.kafkaOffset match {
      case LATEST() => consumer.setStartFromLatest()
      case EARLIEST() => consumer.setStartFromEarliest()
      case GROUP() => consumer.setStartFromGroupOffsets()
      case SPECIFIC(offsets) =>
        val scalaMap = offsets map { case (partition, offset) =>
          (new KafkaTopicPartition(config.consumerTopic, partition), long2Long(offset))
        }
        consumer.setStartFromSpecificOffsets(scalaMap.asJava)
      case TIMESTAMP(start, _) =>
        consumer.setStartFromTimestamp(long2Long(start))
    }
  }
}
