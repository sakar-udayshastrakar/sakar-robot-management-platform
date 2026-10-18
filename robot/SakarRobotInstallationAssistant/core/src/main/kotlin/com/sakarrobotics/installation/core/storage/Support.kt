package com.sakarrobotics.installation.core.storage

import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

fun interface Clock {
    fun nowMillis(): Long
}

object SystemClock : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

interface IdGenerator {
    /** Short opaque id with a readable [prefix], e.g. `map-3f9a1c`. */
    fun next(prefix: String): String
}

object RandomIdGenerator : IdGenerator {
    override fun next(prefix: String): String = prefix + "-" + UUID.randomUUID().toString().substring(0, 6)
}

/** Deterministic ids for tests: `map-000001`, `map-000002`, ... */
class SequentialIdGenerator : IdGenerator {
    private val counter = AtomicLong()
    override fun next(prefix: String): String = prefix + "-" + "%06d".format(counter.incrementAndGet())
}
