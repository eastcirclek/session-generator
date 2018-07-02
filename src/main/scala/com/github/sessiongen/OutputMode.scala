package com.github.sessiongen

sealed trait OutputMode
case class Stdout() extends OutputMode
case class Kafka() extends OutputMode