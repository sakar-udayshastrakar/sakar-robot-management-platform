package com.sakarrobotics.c40agent.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
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

/**
 * Wraps the existing [SdkCallLogger] ring buffer (already fed by every SDK
 * call across :sdk/:navigation/:charging/:robot) rather than introducing a
 * second, competing log pipeline. Export writes a real file to the
 * device's Downloads collection via MediaStore - no fake "export
 * succeeded" toast without an actual file landing on disk.
 */
class LogsRepositoryImpl(private val appContext: Context) : LogsRepository {

    override fun tail(limit: Int): List<LogRecord> =
        SdkCallLogger.getInstance().entries.takeLast(limit).map { it.toDomain() }

    override val liveLog: Flow<LogRecord> = callbackFlow {
        val listener = SdkCallLogger.Listener { entry -> trySend(entry.toDomain()) }
        SdkCallLogger.getInstance().addListener(listener)
        awaitClose { SdkCallLogger.getInstance().removeListener(listener) }
    }

    override suspend fun export(request: LogExportRequest): LogExportResult {
        val fromMillis = request.fromMillis
        val toMillis = request.toMillis
        val entries = SdkCallLogger.getInstance().entries.filter { entry ->
            (fromMillis == null || entry.timestamp >= fromMillis) &&
                (toMillis == null || entry.timestamp <= toMillis)
        }
        val fileName = "sakar_cleanbot_log_${System.currentTimeMillis()}.txt"
        val body = entries.joinToString("\n") { it.toString() }
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

    override fun clear() = SdkCallLogger.getInstance().clear()

    private fun LogEntry.toDomain() = LogRecord(
        timestampMillis = timestamp,
        source = LogSource.SDK_CALL,
        level = if (isSuccess) LogLevel.INFO else LogLevel.ERROR,
        tag = api,
        message = toString()
    )
}
