package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommunicationAndEventLogTest {

    @Test
    fun `tracker starts empty and only a successful call records evidence`() = runTest {
        var now = 100L
        val tracker = CommunicationTracker { now }
        assertNull(tracker.lastSuccessMillis.first())

        val failed = tracker.call { it.onError(-1, "boom") }
        assertTrue(failed.isFailure)
        assertNull(tracker.lastSuccessMillis.first(), "a failed call must not count as communication")

        now = 250L
        val ok = tracker.call { it.onSuccess("ok") }
        assertTrue(ok.isSuccess)
        assertEquals(250L, tracker.lastSuccessMillis.first())

        now = 900L
        tracker.call { it.onError(-1, "boom again") }
        assertEquals(250L, tracker.lastSuccessMillis.first(), "later failures must not refresh the timestamp")
    }

    @Test
    fun `an sdk call that throws becomes a failed result instead of crashing`() = runTest {
        val tracker = CommunicationTracker { 1L }
        val result = tracker.call { throw NullPointerException("component is null") }
        assertTrue(result.isFailure)
        val error = result.exceptionOrNull() as SdkCallException
        assertEquals(SYNCHRONOUS_FAILURE_CODE, error.errorCode)
        assertNull(tracker.lastSuccessMillis.first(), "a throwing call is not communication evidence")
    }

    @Test
    fun `event log redacts before storing`() {
        val log = AppEventLog(clock = { 42L })
        log.error("Auth", "login failed password=hunter2")
        val entry = log.entries().single()
        assertFalse(entry.message.contains("hunter2"))
        assertEquals(LogLevel.ERROR, entry.level)
        assertEquals(LogSource.APPLICATION, entry.source)
        assertEquals(42L, entry.timestampMillis)
    }

    @Test
    fun `event log keeps only the newest entries up to capacity`() {
        val log = AppEventLog(capacity = 3)
        (1..5).forEach { log.info("T", "event $it") }
        assertEquals(listOf("event 3", "event 4", "event 5"), log.entries().map { it.message })
    }

    @Test
    fun `event log clear empties the buffer`() {
        val log = AppEventLog()
        log.warn("T", "something")
        log.clear()
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun `export line is redacted and carries level and tag`() {
        val line = formatExportLine(
            com.sakarrobotics.c40agent.domain.model.LogRecord(
                0L, LogSource.APPLICATION, LogLevel.WARN, "Net", "token=abc123 retry"
            )
        )
        assertTrue(line.contains("WARN"))
        assertTrue(line.contains("[Net]"))
        assertFalse(line.contains("abc123"))
        assertTrue(line.startsWith("1970-01-01T00:00:00.000Z"))
    }
}
