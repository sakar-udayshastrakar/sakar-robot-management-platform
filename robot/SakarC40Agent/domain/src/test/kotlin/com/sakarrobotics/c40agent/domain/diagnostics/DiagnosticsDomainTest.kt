package com.sakarrobotics.c40agent.domain.diagnostics

import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.ConnectionDisplayState
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.LogSource
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DiagnosticsDomainTest {

    @Test
    fun `catalog names are unique and every entry explains itself`() {
        val names = CapabilityCatalog.entries.map { it.name }
        assertEquals(names.size, names.toSet().size)
        assertTrue(CapabilityCatalog.entries.all { it.note.isNotBlank() })
    }

    @Test
    fun `catalog covers every non-real vocabulary term and never claims unverified hardware data as real`() {
        val caps = CapabilityCatalog.entries.map { it.capability }.toSet()
        listOf(Capability.UNKNOWN, Capability.UNAVAILABLE, Capability.SIMULATED, Capability.LOCAL, Capability.GATED)
            .forEach { assertTrue(it in caps, "missing $it") }
        val battery = CapabilityCatalog.entries.first { it.name.startsWith("Battery") }
        assertEquals(Capability.UNKNOWN, battery.capability)
    }

    private fun input(
        battery: BatteryState = BatteryState(null, null, null, null, Capability.UNKNOWN),
        diagnostics: DiagnosticsSnapshot? = null,
        events: List<LogRecord> = emptyList(),
        lastComm: Long? = null
    ) = SupportReportInput(
        generatedAtMillis = 1_000_000L,
        appVersion = "1.2.3",
        sdkVersion = "9.9",
        androidVersion = "14",
        link = RobotLinkState.CONNECTED,
        connection = ConnectionDisplayState.UNKNOWN,
        lastCommunicationMillis = lastComm,
        battery = battery,
        diagnostics = diagnostics,
        recentEvents = events
    )

    @Test
    fun `report states unknown things as unknown`() {
        val report = SupportReportBuilder.build(input())
        assertTrue(report.contains("Readout: UNKNOWN"))
        assertTrue(report.contains("none this session"))
        assertTrue(report.contains("Not read this session"))
        assertTrue(report.contains("none recorded this session"))
        assertFalse(report.contains("%"), "no percentage may appear when battery is unknown")
    }

    @Test
    fun `report redacts secrets that slipped into events or raw payloads`() {
        val report = SupportReportBuilder.build(
            input(
                battery = BatteryState(null, null, null, "{\"token\":\"topsecret\"}", Capability.UNKNOWN),
                events = listOf(LogRecord(999_000L, LogSource.APPLICATION, LogLevel.ERROR, "Net", "password=hunter2 failed"))
            )
        )
        assertFalse(report.contains("topsecret"))
        assertFalse(report.contains("hunter2"))
        assertTrue(report.contains("[Net]"))
    }

    @Test
    fun `report marks runtime info unavailable when the link is down`() {
        val snap = DiagnosticsSnapshot(Capability.UNAVAILABLE, 0, 0, 0, null, false, false, 0, null, null, null, null)
        val report = SupportReportBuilder.build(input(diagnostics = snap))
        assertTrue(report.contains("Not available - SDK link is not connected"))
    }
}
