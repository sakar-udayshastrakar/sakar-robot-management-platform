package com.sakarrobotics.c40agent.domain.model

enum class LogLevel { INFO, WARN, ERROR }

enum class LogSource { SDK_CALL, APPLICATION, DIAGNOSTICS }

data class LogRecord(
    val timestampMillis: Long,
    val source: LogSource,
    val level: LogLevel,
    val tag: String,
    val message: String
)

data class LogExportRequest(val fromMillis: Long?, val toMillis: Long?)

data class LogExportResult(val success: Boolean, val destinationDescription: String, val entryCount: Int)
