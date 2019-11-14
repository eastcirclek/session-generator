package com.github.sessiongen.sessiontracker

import java.io.FileInputStream
import java.util.Properties

import com.typesafe.scalalogging.LazyLogging
import scopt.OptionParser

case class Config(consumerTopic: String = "",
                  consumerProp: Properties = new Properties(),
                  producerTopic: String = "",
                  producerProp: Properties = new Properties(),
                  sessionGap: Long = -1,
                  sourceTasks: Int = -1,
                  windowTasks: Int = -1,
                  sinkTasks: Int = -1,
                  watermarkInterval: Long = 50L,
                  maxOutOfOrderness: Long = 0L,
                  checkpointTimeout: Long = 600000L,
                  stateBackend: StateBackendType = NO_STATE_BACKEND(),
                  kafkaOffset: KafkaOffset = LATEST(),
                  restartStrategy: RestartStrategy = NO_RESTART(),
                  externalizedCheckpoint: Boolean = false,
                  deleteExtCkptOnJobCancel: Boolean = false,
                  incrementalCheckpoint: Boolean = false)

object Config extends LazyLogging {

  implicit val propertiesRead: scopt.Read[Properties] =
    scopt.Read.reads { x =>
      val properties = new Properties()
      properties.load(new FileInputStream(x))
      properties
    }

  def get(args: Array[String], programName: String): Config = {
    val parser = new OptionParser[Config](programName) {
      override def reportError(msg: String): Unit = logger.error(msg)
      override def reportWarning(msg: String): Unit = logger.warn(msg)

      help("help").text("prints this usage text")

      opt[String]("consumer-topic")
        .required()
        .action((x, c) => c.copy(consumerTopic = x))

      opt[Properties]("consumer-prop-file")
        .required()
        .action((x, c) => c.copy(consumerProp = x))

      opt[String]("producer-topic")
        .required()
        .action((x, c) => c.copy(producerTopic = x))

      opt[Properties]("producer-prop-file")
        .required()
        .action((x, c) => c.copy(producerProp = x))

      opt[Long]("session-gap")
        .required()
        .action((x, c) => c.copy(sessionGap = x))
        .valueName("<ms>")

      opt[Int]("source-tasks")
        .required()
        .action((x, c) => c.copy(sourceTasks = x))
        .valueName("<num>")

      opt[Int]("window-tasks")
        .required()
        .action((x, c) => c.copy(windowTasks = x ))
        .valueName("<num>")

      opt[Int]("sink-tasks")
        .required()
        .action((x, c) => c.copy(sinkTasks = x))
        .valueName("<num>")

      opt[Long]("watermark-interval")
        .action((x, c) => c.copy(watermarkInterval = x))
        .valueName("<ms>")
        .text("default : 50")

      opt[Long]("max-out-of-orderness")
        .action((x, c) => c.copy(maxOutOfOrderness = x))
        .valueName("<ms>")
        .text("default : 0")

      opt[Long]("checkpoint-timeout")
        .action((x, c) => c.copy(checkpointTimeout = x))
        .valueName("<ms>")
        .text("default : 600000")

      opt[StateBackendType]("state-backend")
        .action((x, c) => c.copy(stateBackend = x))
        .valueName(StateBackendType.FORMAT)
        .text("default : memory:interval=500")

      opt[KafkaOffset]("kafka-offset")
        .action((x, c) => c.copy(kafkaOffset = x))
        .valueName(KafkaOffset.FORMAT)
        .text("default : latest")

      opt[RestartStrategy]("restart-strategy")
        .action((x, c) => c.copy(restartStrategy = x))
        .valueName(RestartStrategy.FORMAT)
        .text("default : no-restart")

      opt[Unit]("externalized-checkpoint")
        .action((x, c) => c.copy(externalizedCheckpoint = true))

      opt[Unit]("externalized-checkpoint-deletion-on-job-cancel")
        .action((x, c) => c.copy(deleteExtCkptOnJobCancel = true))

      opt[Unit]("incremental-checkpoint")
        .action((x, c) => c.copy(incrementalCheckpoint = true))
        .text("effective only when using a rocksdb state-backend")
    }

    parser.parse(args, Config()) match {
      case Some(config) => config
      case None => throw new RuntimeException("Failed to get a valid configuration object")
    }
  }
}
