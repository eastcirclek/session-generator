package com.github.sessiongen.sessiontracker

import java.lang
import java.nio.charset.StandardCharsets

import com.github.sessiongen.Event
import org.apache.flink.api.common.serialization.SimpleStringSchema
import org.apache.flink.api.common.typeinfo.Types
import org.apache.flink.shaded.zookeeper.org.apache.zookeeper.server.SessionTracker
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.datastream.{DataStream, SingleOutputStreamOperator}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.functions.windowing.{ProcessWindowFunction, WindowFunction}
import org.apache.flink.streaming.api.windowing.assigners.EventTimeSessionWindows
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.windows.TimeWindow
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer, KafkaSerializationSchema}
import org.apache.flink.util.Collector
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s._
import org.json4s.JsonDSL.WithDouble._
import org.json4s.native.Serialization.{read, write}

import scala.collection.JavaConverters._

object SessionTracker {
  implicit val formats = DefaultFormats

  def main(args: Array[String]): Unit = {
    val config = TrackerConfig.get(args, classOf[SessionTracker].getName())

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.getConfig.setAutoWatermarkInterval(config.watermarkInterval)
    StateBackendType.setupEnvironment(env, config)
    RestartStrategy.setupEnvironment(env, config)
    Checkpoint.setupEnvironment(env, config)

    val consumer = new FlinkKafkaConsumer[String](config.consumerTopic, new SimpleStringSchema(), config.consumerProp)
    val producer = new FlinkKafkaProducer[String](
      config.producerTopic,
      new KafkaSerializationSchema[String] {
        override def serialize(element: String, timestamp: lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
          new ProducerRecord[Array[Byte], Array[Byte]](config.producerTopic, element.getBytes(StandardCharsets.UTF_8))
        }
      },
      config.consumerProp,
      FlinkKafkaProducer.Semantic.AT_LEAST_ONCE)

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

    val window: DataStream[String] = source
      .keyBy(_.id)
      .window(EventTimeSessionWindows.withGap(Time.milliseconds(config.sessionGap)))
      .process { (key: String, ctx: ProcessWindowFunction[Event, String, String, TimeWindow]#Context, elements, out: Collector[String]) =>
        out.collect(
          write(
            ("key" -> key) ~
              ("windowStart" -> ctx.window().getStart) ~
              ("windowEnd" -> ctx.window().getStart) ~
              ("messages" -> elements.asScala.map(write[Event]))
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
