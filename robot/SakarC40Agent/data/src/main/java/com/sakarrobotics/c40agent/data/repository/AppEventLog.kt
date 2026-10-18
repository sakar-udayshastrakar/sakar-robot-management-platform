package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.logging.AppEventLogger
import com.sakarrobotics.c40agent.domain.logging.LogRedactor
import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.LogSource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory ring buffer of application events (not SDK calls - those live in SdkCallLogger).
 * Every message is passed through [LogRedactor] *before* it is stored, so nothing downstream (log
 * screen, export, support report) can ever see an unredacted secret. Nothing is persisted: the log
 * is cleared when the process ends.
 */
class AppEventLog(
    private val capacity: Int = 300,
    private val clock: () -> Long = System::currentTimeMillis
) : AppEventLogger {

    private val lock = Any()
    private val buffer = ArrayDeque<LogRecord>()
    private val _live = MutableSharedFlow<LogRecord>(extraBufferCapacity = 64)
    val live: SharedFlow<LogRecord> = _live.asSharedFlow()

    override fun log(level: LogLevel, subsystem: String, message: String) {
        val record = LogRecord(
            timestampMillis = clock(),
            source = LogSource.APPLICATION,
            level = level,
            tag = LogRedactor.redact(subsystem).take(MAX_TAG),
            message = LogRedactor.redact(message).take(MAX_MESSAGE)
        )
        synchronized(lock) {
            buffer.addLast(record)
            while (buffer.size > capacity) buffer.removeFirst()
        }
        _live.tryEmit(record)
    }

    /** Oldest-first snapshot of the buffered events. */
    fun entries(): List<LogRecord> = synchronized(lock) { buffer.toList() }

    fun clear() = synchronized(lock) { buffer.clear() }

    private companion object {
        const val MAX_TAG = 40
        const val MAX_MESSAGE = 600
    }
}
