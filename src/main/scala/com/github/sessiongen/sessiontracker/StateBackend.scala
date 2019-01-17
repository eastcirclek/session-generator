package com.github.sessiongen.sessiontracker

import org.apache.flink.contrib.streaming.state.{PredefinedOptions, RocksDBStateBackend}
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.runtime.state.memory.MemoryStateBackend
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment

sealed trait StateBackend
case class NO_STATE_BACKEND() extends StateBackend
case class MEMORY_STATE_BACKEND(intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackend
case class FS_STATE_BACKEND(uri: String, intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackend
case class ROCKSDB_STATE_BACKEND(uri: String, intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackend

object StateBackend {
  val NONE_FORMAT = "none"
  val MEMORY_FORMAT = "memory:interval=<ms>[,pause=<ms>]"
  val FS_FORMAT = "fs:uri=<uri>,interval=<ms>[,pause=<ms>]"
  val ROCKSDB_FORMAT = "rocksdb:uri=<uri>,interval=<ms>[,pause=<ms>]"
  val FORMAT: String = s"($MEMORY_FORMAT|$FS_FORMAT|$ROCKSDB_FORMAT)"

  def parse: String => StateBackend = {
    case "none" =>
      NO_STATE_BACKEND()

    case x if x.startsWith("memory:") =>
      val (_, options) = x.splitAt(x.indexOf(':')+1)
      val map = implicitly[scopt.Read[Map[String, String]]].reads(options)
      MEMORY_STATE_BACKEND(map("interval").toLong, map.get("pause").map(_.toLong))

    case x if x.startsWith("fs:") =>
      val (_, options) = x.splitAt(x.indexOf(':')+1)
      val map = implicitly[scopt.Read[Map[String, String]]].reads(options)
      FS_STATE_BACKEND(map("uri"), map("interval").toLong, map.get("pause").map(_.toLong))

    case x if x.startsWith("rocksdb:") =>
      val (_, options) = x.splitAt(x.indexOf(':')+1)
      val map = implicitly[scopt.Read[Map[String, String]]].reads(options)
      ROCKSDB_STATE_BACKEND(map("uri"), map("interval").toLong, map.get("pause").map(_.toLong))

    case x =>
      throw new IllegalArgumentException(s"Unknown input $x while the expected input is of the format $FORMAT")
  }

  implicit val read: scopt.Read[StateBackend] = scopt.Read.reads(parse)

  def setupEnvironment(env: StreamExecutionEnvironment, config: Config): Unit ={
    import config._

    stateBackend match {
      case NO_STATE_BACKEND() => ()

      case MEMORY_STATE_BACKEND(intervalInMillis, pauseMillisOpt) =>
        env.setStateBackend(new MemoryStateBackend())
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))

      case FS_STATE_BACKEND(uri, intervalInMillis, pauseMillisOpt) =>
        env.setStateBackend(new FsStateBackend(uri))
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))

      case ROCKSDB_STATE_BACKEND(uri, intervalInMillis, pauseMillisOpt) =>
        val rocksDBStateBackend = new RocksDBStateBackend(uri, config.incrementalCheckpoint)
        env.setStateBackend(rocksDBStateBackend)
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))
    }
  }
}