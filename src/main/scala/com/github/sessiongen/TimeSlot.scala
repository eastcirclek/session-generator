package com.github.sessiongen

import scala.util.Random

object TimeSlot {
  private val randomGenerator = new Random(System.nanoTime)
}

class TimeSlot(firstId: Int, lastId: Int, logoutProbability: Double) {
  private var currentId = firstId
  private var cnt = 0

  var history: List[(Int, Int)] = List()

  def getUserIdAndWhetherLoggedOut(): (Int, Boolean) = {
    val userId = currentId
    cnt += 1

    val shouldLogout = TimeSlot.randomGenerator.nextFloat() < logoutProbability
    if (shouldLogout) {
      logout()

      if (currentId < lastId)
        currentId += 1
      else
        currentId = firstId
    }

    (userId, shouldLogout)
  }

  def logout(): Unit = {
    if (cnt > 0) {
      history = (currentId, cnt)::history
    }
    cnt = 0
  }
}
