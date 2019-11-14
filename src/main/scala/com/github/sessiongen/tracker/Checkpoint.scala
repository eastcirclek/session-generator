package com.github.sessiongen.sessiontracker

import org.apache.flink.streaming.api.environment.CheckpointConfig.ExternalizedCheckpointCleanup
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment

object Checkpoint {
  def setupEnvironment(env: StreamExecutionEnvironment, config: TrackerConfig): Unit = {
    import config._

    env.getCheckpointConfig.setCheckpointTimeout(checkpointTimeout)
    if (externalizedCheckpoint) {
      env.getCheckpointConfig.enableExternalizedCheckpoints(
        if (deleteExtCkptOnJobCancel) {
          ExternalizedCheckpointCleanup.DELETE_ON_CANCELLATION
        } else {
          ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        }
      )
    }
  }
}
