package com.sakarrobotics.c40agent.domain.logging

import com.sakarrobotics.c40agent.domain.model.LogLevel

/**
 * Application-side event sink (connection changes, repository errors, unsupported capability
 * requests, UI operation failures). Deliberately separate from the raw Peanut SDK call log: that
 * one records every SDK exchange, this one records what the *app* decided or failed to do.
 * Implementations must redact secrets (see [LogRedactor]) before storing anything.
 */
interface AppEventLogger {
    fun log(level: LogLevel, subsystem: String, message: String)

    fun info(subsystem: String, message: String) = log(LogLevel.INFO, subsystem, message)
    fun warn(subsystem: String, message: String) = log(LogLevel.WARN, subsystem, message)
    fun error(subsystem: String, message: String) = log(LogLevel.ERROR, subsystem, message)
}

/** Used where no logger is wired (previews, tests); drops everything. */
object NoOpAppEventLogger : AppEventLogger {
    override fun log(level: LogLevel, subsystem: String, message: String) = Unit
}

/**
 * Best-effort secret scrubbing for anything that may leave the app (log view, export, support
 * report). It targets common credential shapes; it is a safety net, not a guarantee, so callers
 * should still avoid logging secrets in the first place.
 */
object LogRedactor {
    const val MASK = "[REDACTED]"

    private val sensitiveKeys =
        "password|passwd|pwd|pin|secret|token|access_token|refresh_token|api[_-]?key|authorization|" +
            "credential|private[_-]?key|client[_-]?secret"

    private val pemPrivateKey =
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]*?-----END [A-Z ]*PRIVATE KEY-----")
    private val urlUserInfo = Regex("([a-zA-Z][a-zA-Z0-9+.\\-]*://)[^/\\s:@]+:[^/\\s@]+@")
    private val jwt = Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
    private val bearerOrBasic = Regex("\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/\\-]+=*", RegexOption.IGNORE_CASE)
    private val keyValue = Regex(
        "(?i)(\\b(?:$sensitiveKeys)\\b)(\"?\\s*[:=]\\s*)(\"[^\"]*\"|'[^']*'|[^\\s,;&}\\]]+)"
    )
    private val longHex = Regex("\\b[A-Fa-f0-9]{40,}\\b")

    fun redact(text: String): String {
        var result = text
        result = pemPrivateKey.replace(result, "[REDACTED PRIVATE KEY]")
        result = urlUserInfo.replace(result) { it.groupValues[1] + MASK + "@" }
        result = jwt.replace(result, MASK)
        result = bearerOrBasic.replace(result) { it.groupValues[1] + " " + MASK }
        result = keyValue.replace(result) { it.groupValues[1] + it.groupValues[2] + MASK }
        result = longHex.replace(result, MASK)
        return result
    }
}
