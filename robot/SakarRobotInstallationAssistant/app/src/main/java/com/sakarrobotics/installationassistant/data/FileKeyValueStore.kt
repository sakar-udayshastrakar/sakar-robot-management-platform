package com.sakarrobotics.installationassistant.data

import com.sakarrobotics.installation.core.storage.KeyValueStore
import com.sakarrobotics.installation.core.storage.StorageException
import java.io.File
import java.io.IOException

/**
 * Stores each key as a small JSON file in the app's private storage. Writes go to a temporary file
 * first and are then renamed, so a crash mid-write cannot leave a half-written value. The app stores
 * demo data only - never credentials or personal information.
 */
class FileKeyValueStore(private val dir: File) : KeyValueStore {

    private val lock = Any()

    init {
        dir.mkdirs()
    }

    private fun fileFor(key: String) = File(dir, key.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".json")

    override fun get(key: String): String? = synchronized(lock) {
        val file = fileFor(key)
        try {
            if (file.exists()) file.readText() else null
        } catch (e: IOException) {
            null
        }
    }

    override fun put(key: String, value: String) {
        synchronized(lock) {
            val target = fileFor(key)
            val temp = File(dir, target.name + ".tmp")
            try {
                temp.writeText(value)
                if (!temp.renameTo(target)) {
                    target.delete()
                    if (!temp.renameTo(target)) throw IOException("rename failed")
                }
            } catch (e: IOException) {
                temp.delete()
                throw StorageException("could not write ${target.name}", e)
            }
            Unit
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            val file = fileFor(key)
            if (file.exists() && !file.delete()) throw StorageException("could not delete ${file.name}")
        }
    }
}
