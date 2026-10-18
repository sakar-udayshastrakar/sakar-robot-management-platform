package com.sakarrobotics.c40agent.domain.diagnostics

import com.sakarrobotics.c40agent.domain.logging.LogRedactor
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.ConnectionDisplayState
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.batteryReadout
import com.sakarrobotics.c40agent.domain.model.formatAge

data class SupportReportInput(
    val generatedAtMillis: Long,
    val appVersion: String?,
    val sdkVersion: String?,
    val androidVersion: String?,
    val link: RobotLinkState,
    val connection: ConnectionDisplayState,
    val lastCommunicationMillis: Long?,
    val battery: BatteryState,
    /** Null when the operator has not read runtime info this session. */
    val diagnostics: DiagnosticsSnapshot?,
    val recentEvents: List<LogRecord>
)

/**
 * Builds the plain-text report an operator can copy and send to support. It reports only what the
 * app actually holds, labels every section's provenance, and runs the whole text through
 * [LogRedactor] as a last step. It deliberately leaves out the robot's IP address and any
 * identifiers.
 */
object SupportReportBuilder {

    fun build(input: SupportReportInput): String {
        val now = input.generatedAtMillis
        val lines = mutableListOf<String>()
        lines += "Sakar CleanBot support report"
        lines += "Generated (epoch ms): $now"
        lines += "App version: ${input.appVersion ?: "unknown"}"
        lines += "SDK version: ${input.sdkVersion ?: "unknown"}"
        lines += "Android version: ${input.androidVersion ?: "unknown"}"
        lines += ""
        lines += "Connection"
        lines += "  SDK link: ${input.link}"
        lines += "  Display state: ${input.connection}"
        lines += "  Last successful SDK exchange: " +
            (input.lastCommunicationMillis?.let { formatAge(it, now) } ?: "none this session")
        lines += ""
        lines += "Battery [${input.battery.capability}]"
        lines += "  Readout: ${batteryReadout(input.battery)}"
        lines += "  Raw response: ${input.battery.raw ?: "none"}"
        lines += ""
        lines += "Runtime info"
        val diag = input.diagnostics
        if (diag == null) {
            lines += "  Not read this session"
        } else if (diag.capability == Capability.UNAVAILABLE) {
            lines += "  [${diag.capability}] Not available - SDK link is not connected"
        } else {
            lines += "  [${diag.capability}] work mode ${diag.workMode}, power ${diag.power}, " +
                "sync ${diag.syncStatus}, motor status ${diag.motorStatusCode}"
            lines += "  Emergency stop engaged: ${diag.emergencyStopEngaged}, enabled: ${diag.emergencyEnabled}"
        }
        lines += ""
        lines += "Capability summary"
        CapabilityCatalog.entries.forEach { lines += "  [${it.capability}] ${it.name}" }
        lines += ""
        lines += "Recent warnings and errors (newest first)"
        if (input.recentEvents.isEmpty()) {
            lines += "  none recorded this session"
        } else {
            input.recentEvents.forEach {
                lines += "  ${formatAge(it.timestampMillis, now)} ${it.level} [${it.tag}] ${it.message}"
            }
        }
        return LogRedactor.redact(lines.joinToString("\n"))
    }
}
