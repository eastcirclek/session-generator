package com.github.sessiongen.generator

sealed trait OutputMode
case class Stdout() extends OutputMode
case class Kafka() extends OutputMode