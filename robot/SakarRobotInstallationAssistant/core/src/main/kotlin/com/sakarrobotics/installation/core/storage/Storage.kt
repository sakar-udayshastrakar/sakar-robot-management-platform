package com.sakarrobotics.installation.core.storage

import com.google.gson.Gson
import java.util.concurrent.ConcurrentHashMap

/**
 * Minimal string key/value persistence for the local demo data. Implementations must never be
 * used for credentials or personal data - this release stores none.
 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(key: String)
}

class InMemoryKeyValueStore : KeyValueStore {
    private val data = ConcurrentHashMap<String, String>()
    override fun get(key: String): String? = data[key]
    override fun put(key: String, value: String) { data[key] = value }
    override fun remove(key: String) { data.remove(key) }
}

/** JSON helpers. A corrupt or incompatible stored value never crashes the app: it falls back to a default. */
internal object Json {
    val gson: Gson = Gson()

    inline fun <reified T> load(store: KeyValueStore, key: String, fallback: () -> T): T {
        val raw = store.get(key) ?: return fallback()
        return try {
            gson.fromJson(raw, T::class.java) ?: fallback()
        } catch (e: Exception) {
            fallback()
        }
    }

    inline fun <reified T : Any> loadOrNull(store: KeyValueStore, key: String): T? {
        val raw = store.get(key) ?: return null
        return try {
            gson.fromJson(raw, T::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun <T> loadList(store: KeyValueStore, key: String, elementType: Class<Array<T>>, fallback: () -> List<T>): List<T> {
        val raw = store.get(key) ?: return fallback()
        return try {
            gson.fromJson(raw, elementType)?.toList() ?: fallback()
        } catch (e: Exception) {
            fallback()
        }
    }

    fun save(store: KeyValueStore, key: String, value: Any?) {
        if (value == null) store.remove(key) else store.put(key, gson.toJson(value))
    }
}
