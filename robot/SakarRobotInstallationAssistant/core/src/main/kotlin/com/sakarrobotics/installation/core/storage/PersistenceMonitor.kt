package com.sakarrobotics.installation.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Thrown by a [KeyValueStore] when a value could not be written or removed. */
class StorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Surfaces storage failures to the UI. A failed write never crashes the app and never throws out of a
 * repository: the in-memory state stays usable, and [error] tells the user the change may be lost on
 * restart. The error clears when every key that failed has since been written successfully (or the
 * user dismisses it).
 */
class PersistenceMonitor {
    private val failures = LinkedHashMap<String, String>()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun failed(key: String, operation: String, cause: Throwable) = synchronized(failures) {
        failures[key] = "Could not $operation on this tablet (${cause.message ?: cause.javaClass.simpleName}). " +
            "Recent changes may be lost if the app closes."
        publish()
    }

    internal fun succeeded(key: String) = synchronized(failures) {
        if (failures.remove(key) != null) publish()
    }

    fun dismiss() = synchronized(failures) {
        failures.clear()
        publish()
    }

    private fun publish() {
        _error.value = failures.values.lastOrNull()
    }
}

/** Wraps a store so write failures are reported to [monitor] instead of propagating. */
class MonitoredKeyValueStore(
    private val delegate: KeyValueStore,
    private val monitor: PersistenceMonitor
) : KeyValueStore {

    override fun get(key: String): String? = try {
        delegate.get(key)
    } catch (e: StorageException) {
        monitor.failed(key, "read saved data", e)
        null
    }

    override fun put(key: String, value: String) {
        try {
            delegate.put(key, value)
            monitor.succeeded(key)
        } catch (e: StorageException) {
            monitor.failed(key, "save changes", e)
        }
    }

    override fun remove(key: String) {
        try {
            delegate.remove(key)
            monitor.succeeded(key)
        } catch (e: StorageException) {
            monitor.failed(key, "remove saved data", e)
        }
    }
}
