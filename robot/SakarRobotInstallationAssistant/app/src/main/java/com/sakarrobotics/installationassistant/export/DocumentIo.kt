package com.sakarrobotics.installationassistant.export

import android.content.ContentResolver
import android.net.Uri
import com.sakarrobotics.installation.core.backup.BackupFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * Reads and writes backup text at a user-chosen location through the Storage Access Framework
 * ([ContentResolver] with a URI from the system file picker). The app never needs storage permissions
 * and never browses the file system itself.
 */
class DocumentIo(private val resolver: ContentResolver) {

    fun readText(uri: Uri): Result<String> = try {
        val input = resolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_BYTES) throw IOException("The file is too large to be a backup from this app.")
            }
            out.toByteArray()
        }
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        Result.success(decoder.decode(ByteBuffer.wrap(bytes)).toString().removePrefix("﻿"))
    } catch (e: CharacterCodingException) {
        Result.failure(IOException("The file is not a text file, so it cannot be a backup.", e))
    } catch (e: IOException) {
        Result.failure(IOException(e.message ?: "The file could not be read.", e))
    } catch (e: SecurityException) {
        Result.failure(IOException("The file could not be read: access was denied.", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(IOException("The file location is not valid.", e))
    }

    fun writeText(uri: Uri, text: String): Result<Unit> = try {
        val output = resolver.openOutputStream(uri, "wt") ?: throw IOException("The file could not be opened for writing.")
        output.use {
            it.write(text.toByteArray(Charsets.UTF_8))
            it.flush()
        }
        Result.success(Unit)
    } catch (e: IOException) {
        Result.failure(IOException(e.message ?: "The file could not be written.", e))
    } catch (e: SecurityException) {
        Result.failure(IOException("The file could not be written: access was denied.", e))
    } catch (e: IllegalArgumentException) {
        Result.failure(IOException("The file location is not valid.", e))
    }

    companion object {
        /** Most bytes read from a picked file; the backup parser separately caps the decoded length. */
        const val MAX_BYTES = BackupFormat.MAX_CHARS
    }
}
