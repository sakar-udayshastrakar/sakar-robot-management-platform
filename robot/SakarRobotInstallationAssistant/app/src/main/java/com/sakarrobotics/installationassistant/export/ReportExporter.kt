package com.sakarrobotics.installationassistant.export

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.sakarrobotics.installation.core.logic.ReportDocumentBuilder
import com.sakarrobotics.installation.core.model.InstallationReport
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Result of creating a PDF: the file plus what was drawn into it. */
data class ExportedPdf(val file: File, val render: PdfRenderResult)

/**
 * Creates the installation report PDF in [directory] (the app's private cache, never shared storage).
 * Failures are returned, never thrown, so the UI can show an understandable message.
 */
class ReportExporter(private val directory: File) {

    fun export(report: InstallationReport): Result<ExportedPdf> {
        val target = File(directory, fileName(report))
        return try {
            if (!directory.exists() && !directory.mkdirs()) throw IOException("Could not create the export folder.")
            if (!directory.isDirectory) throw IOException("The export location is not a folder.")
            pruneOldFiles(keep = target)
            val temp = File(directory, target.name + ".part")
            val rendered = try {
                FileOutputStream(temp).use { PdfReportRenderer().render(ReportDocumentBuilder.build(report), it) }
            } catch (e: Exception) {
                temp.delete()
                throw e
            }
            if (rendered.pageCount < 1 || temp.length() == 0L) {
                temp.delete()
                throw IOException("The PDF came out empty.")
            }
            if (target.exists() && !target.delete()) throw IOException("Could not replace the previous PDF.")
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Could not save the PDF.")
            }
            Result.success(ExportedPdf(target, rendered))
        } catch (e: IOException) {
            Result.failure(PdfExportException("The PDF could not be created: ${e.message ?: "storage error"}.", e))
        } catch (e: SecurityException) {
            Result.failure(PdfExportException("The PDF could not be created: storage access was denied.", e))
        } catch (e: RuntimeException) {
            // PdfDocument can throw IllegalStateException/IllegalArgumentException.
            Result.failure(PdfExportException("The PDF could not be created (${e.javaClass.simpleName}).", e))
        }
    }

    /** Keeps the cache small: only a handful of recent PDFs are retained. */
    private fun pruneOldFiles(keep: File) {
        val files = directory.listFiles { f -> f.isFile && (f.name.endsWith(".pdf") || f.name.endsWith(".part")) } ?: return
        files.filter { it != keep }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_KEPT - 1)
            .forEach { it.delete() }
    }

    companion object {
        private const val MAX_KEPT = 10

        fun fileName(report: InstallationReport): String =
            "installation-report-${safe(report.installationId)}-${safe(report.id)}.pdf"

        private fun safe(value: String) = value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
    }
}

class PdfExportException(message: String, cause: Throwable?) : Exception(message, cause)

/** Hands a generated PDF to the Android share sheet through a [FileProvider] URI (no storage permission needed). */
object ReportSharer {

    fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun share(context: Context, pdf: File): Result<Unit> = try {
        val uri = FileProvider.getUriForFile(context, authority(context), pdf)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Installation report (SIMULATION ONLY)")
            clipData = ClipData.newRawUri("Installation report", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share installation report (PDF)")
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        Result.success(Unit)
    } catch (e: ActivityNotFoundException) {
        Result.failure(PdfExportException("No app on this tablet can receive a PDF to share.", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(PdfExportException("The PDF could not be prepared for sharing.", e))
    } catch (e: SecurityException) {
        Result.failure(PdfExportException("Sharing was blocked by the system.", e))
    }
}
