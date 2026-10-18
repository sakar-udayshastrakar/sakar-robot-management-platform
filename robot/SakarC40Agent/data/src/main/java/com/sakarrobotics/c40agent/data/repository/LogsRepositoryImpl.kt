package com.sakarrobotics.c40agent.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import com.sakarrobotics.c40agent.domain.logging.LogRedactor
import com.sakarrobotics.c40agent.domain.model.LogExportRequest
import com.sakarrobotics.c40agent.domain.model.LogExportResult
import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.LogSource
import com.sakarrobotics.c40agent.domain.repository.LogsRepository
import com.sakarrobotics.c40agent.logging.LogEntry
import com.sakarrobotics.c40agent.logging.SdkCallLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge

/**
 * Merges the existing [SdkCallLogger] ring buffer (already fed by every SDK
 * call across :sdk/:navigation/:charging/:robot) with the application's own
 * [AppEventLog], rather than introducing a competing pipeline. Everything
 * returned or exported is passed through [LogRedactor]. Export writes a real
 * file to the device's Downloads collection via MediaStore - no fake "export
 * succeeded" toast without an actual file landing on disk.
 */
class LogsRepositoryImpl(
    private val appContext: Context,
    private val appEvents: AppEventLog = AppEventLog()
) : LogsRepository {

    override fun tail(limit: Int): List<LogRecord> = allRecords().takeLast(limit)

    override val liveLog: Flow<LogRecord> = merge(
        callbackFlow {
            val listener = SdkCallLogger.Listener { entry -> trySend(entry.toDomain()) }
            SdkCallLogger.getInstance().addListener(listener)
            awaitClose { SdkCallLogger.getInstance().removeListener(listener) }
        },
        appEvents.live
    )

    private fun allRecords(): List<LogRecord> =
        (SdkCallLogger.getInstance().entries.map { it.toDomain() } + appEvents.entries())
            .sortedBy { it.timestampMillis }

    override suspend fun export(request: LogExportRequest): LogExportResult {
        val fromMillis = request.fromMillis
        val toMillis = request.toMillis
        val entries = allRecords().filter { entry ->
            (fromMillis == null || entry.timestampMillis >= fromMillis) &&
                (toMillis == null || entry.timestampMillis <= toMillis)
        }
        val fileName = "sakar_cleanbot_log_${System.currentTimeMillis()}.txt"
        val body = entries.joinToString("\n") { formatExportLine(it) }
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SakarCleanBot")
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return LogExportResult(false, "Could not create export file", 0)
            resolver.openOutputStream(uri)?.use { it.write(body.toByteArray()) }
            LogExportResult(true, "Downloads/SakarCleanBot/$fileName", entries.size)
        } catch (e: Exception) {
            LogExportResult(false, e.message ?: "Export failed", 0)
        }
    }

    override fun clear() {
        SdkCallLogger.getInstance().clear()
        appEvents.clear()
    }

    private fun LogEntry.toDomain() = LogRecord(
        timestampMillis = timestamp,
        source = LogSource.SDK_CALL,
        level = if (isSuccess) LogLevel.INFO else LogLevel.ERROR,
        tag = api,
        // LogEntry.toString() starts with its raw epoch-millis timestamp, which the record already carries.
        message = LogRedactor.redact(toString().replaceFirst(LEADING_EPOCH, ""))
    )

    private companion object {
        val LEADING_EPOCH = Regex("^\\d{10,}\\s+")
    }
}

/** One export line: ISO-like UTC timestamp, source, level, tag, message - redacted. */
internal fun formatExportLine(record: LogRecord): String {
    // SimpleDateFormat (not java.time) - minSdk is 21; a fresh instance per call as it is not thread-safe.
    val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
    format.timeZone = java.util.TimeZone.getTimeZone("UTC")
    val time = format.format(java.util.Date(record.timestampMillis))
    return "$time ${record.source} ${record.level} [${record.tag}] ${LogRedactor.redact(record.message)}"
}
