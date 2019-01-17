package com.github.sessiongen.sessiontracker

import com.github.sessiongen.Event
import com.github.sessiongen.sessiontracker._
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.apache.flink.util.Collector
import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.Serialization.{read, write}

object SessionTracker {
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {
    val config = Config.get(args, "SessionTracker")

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.getConfig.setAutoWatermarkInterval(config.watermarkInterval)
    StateBackend.setupEnvironment(env, config)
    RestartStrategy.setupEnvironment(env, config)
    Checkpoint.setupEnvironment(env, config)

    val consumer = new FlinkKafkaConsumer[String](config.consumerTopic, new SimpleStringSchema(), config.consumerProp)
    val producer = new FlinkKafkaProducer[String](config.producerTopic, new SimpleStringSchema(), config.consumerProp)

    KafkaOffset.setupConsumer(consumer, config)
    val source: DataStream[Event] = env
      .addSource(consumer)
      .setParallelism(config.sourceTasks)
      .name("source")
      .uid("source")
      .map(read[Event](_))
      .setParallelism(config.sourceTasks)
      .name("map")
      .uid("map")
      .assignTimestampsAndWatermarks(
        new BoundedOutOfOrdernessTimestampExtractor[Event](Time.milliseconds(config.maxOutOfOrderness)) {
          override def extractTimestamp(req: Event): Long = req.timestamp
        }
      )
      .name("watermark")
      .uid("watermark")

    val window = source
      .keyBy(_.id)
      .window(EventTimeSessionWindows.withGap(Time.milliseconds(config.sessionGap)))
      .apply { (key, window, iter, collector: Collector[String]) =>
        collector.collect(
          write(
            ("key" -> key) ~
            ("windowStart" -> window.getStart) ~
            ("windowEnd" -> window.getEnd) ~
            ("messages" -> iter.map(write[Event]))
          )
        )
      }
      .setParallelism(1)
      .name("window")
      .uid("window")

    window
      .addSink(producer)
      .setParallelism(config.sinkTasks)
      .name("sink")
      .uid("sink")

    env.execute()
  }
}
