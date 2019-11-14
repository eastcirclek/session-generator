package com.github.sessiongen.tracker

import com.github.sessiongen.generator.{Generator, GeneratorConfig}
import com.github.sessiongen.sessiontracker.{Checkpoint, NO_RESTART, NO_STATE_BACKEND, RestartStrategy, StateBackendType, TrackerConfig}
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import scopt.OptionParser

private case class Config(sessionGap: Long = -1,
                  sourceTasks: Int = -1,
                  windowTasks: Int = -1,
                  sinkTasks: Int = -1,
                  watermarkInterval: Long = 50L,
                  maxOutOfOrderness: Long = 0L,
                  checkpointTimeout: Long = 600000L,
                  stateBackend: StateBackendType = NO_STATE_BACKEND(),
                  restartStrategy: RestartStrategy = NO_RESTART(),
                  externalizedCheckpoint: Boolean = false,
                  deleteExtCkptOnJobCancel: Boolean = false,
                  incrementalCheckpoint: Boolean = false)

private object Config extends LazyLogging {
  def get(args: Array[String], programName: String): TrackerConfig = {
    val parser = new OptionParser[TrackerConfig](programName) {
      override def reportError(msg: String): Unit = logger.error(msg)
      override def reportWarning(msg: String): Unit = logger.warn(msg)

      help("help").text("prints this usage text")

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

    parser.parse(args, TrackerConfig()) match {
      case Some(config) => config
      case None => throw new RuntimeException("Failed to get a valid configuration object")
    }
  }
}

object SelfGeneratedSessionTracker {
  def main(args: Array[String]): Unit = {
    val generatorConfig = GeneratorConfig.get(args, classOf[Generator].getName)
    val generator = new Generator(generatorConfig, generatorConfig.keyPrefix)

    val config = Config.get(args, SelfGeneratedSessionTracker.getClass.getName)

    val env = StreamExecutionEnvironment.getExecutionEnvironment
    env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)
    env.getConfig.setAutoWatermarkInterval(config.watermarkInterval)
    StateBackendType.setupEnvironment(env, config)
    RestartStrategy.setupEnvironment(env, config)
    Checkpoint.setupEnvironment(env, config)

    def stdoutFeeder(id: String, jsonString: String, timestamp: Long): Boolean = {
      println(jsonString)
      false
    }
    generator.run(stdoutFeeder)
  }
}
