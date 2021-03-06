package com.github.sessiongen.sessiontracker

import org.apache.flink.contrib.streaming.state.RocksDBStateBackend
import org.apache.flink.runtime.state.StateBackend
import org.apache.flink.runtime.state.filesystem.FsStateBackend
import org.apache.flink.runtime.state.memory.MemoryStateBackend
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

sealed trait StateBackendType
case class NO_STATE_BACKEND() extends StateBackendType
case class MEMORY_STATE_BACKEND(intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackendType
case class FS_STATE_BACKEND(uri: String, intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackendType
case class ROCKSDB_STATE_BACKEND(uri: String, intervalInMillis: Long, pauseInMillis: Option[Long]) extends StateBackendType

object StateBackendType {
  val NONE_FORMAT = "none"
  val MEMORY_FORMAT = "memory:interval=<ms>[,pause=<ms>]"
  val FS_FORMAT = "fs:uri=<uri>,interval=<ms>[,pause=<ms>]"
  val ROCKSDB_FORMAT = "rocksdb:uri=<uri>,interval=<ms>[,pause=<ms>]"
  val FORMAT: String = s"($MEMORY_FORMAT|$FS_FORMAT|$ROCKSDB_FORMAT)"

  def parse: String => StateBackendType = {
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

  implicit val read: scopt.Read[StateBackendType] = scopt.Read.reads(parse)

  def setupEnvironment(env: StreamExecutionEnvironment, config: TrackerConfig): Unit ={
    import config._

    stateBackend match {
      case NO_STATE_BACKEND() => ()

      case MEMORY_STATE_BACKEND(intervalInMillis, pauseMillisOpt) =>
        env.setStateBackend(new MemoryStateBackend().asInstanceOf[StateBackend])
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))

      case FS_STATE_BACKEND(uri, intervalInMillis, pauseMillisOpt) =>
        env.setStateBackend(new FsStateBackend(uri).asInstanceOf[StateBackend])
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))

      case ROCKSDB_STATE_BACKEND(uri, intervalInMillis, pauseMillisOpt) =>
        val rocksDBStateBackend = new RocksDBStateBackend(uri, config.incrementalCheckpoint)
        env.setStateBackend(rocksDBStateBackend.asInstanceOf[StateBackend])
        env.enableCheckpointing(intervalInMillis)
        pauseMillisOpt.foreach(env.getCheckpointConfig.setMinPauseBetweenCheckpoints(_))
    }
  }
}