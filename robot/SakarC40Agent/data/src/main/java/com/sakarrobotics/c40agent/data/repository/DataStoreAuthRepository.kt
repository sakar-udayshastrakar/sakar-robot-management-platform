package com.sakarrobotics.c40agent.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sakarrobotics.c40agent.domain.model.UserRole
import com.sakarrobotics.c40agent.domain.repository.AuthRepository
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "sakar_super_user_auth")

/**
 * Super User gate, separate from the normal operator flow. The PIN is
 * never stored or logged in plaintext: only a random salt and a
 * SHA-256(salt + pin) digest are persisted (DataStore, app-private
 * storage). There is no default/backdoor PIN - until an operator sets
 * one, Super User mode cannot be entered at all.
 */
class DataStoreAuthRepository(private val appContext: Context) : AuthRepository {

    private val _role = MutableStateFlow(UserRole.OPERATOR)
    override val role: Flow<UserRole> = _role.asStateFlow()

    private val keySalt = stringPreferencesKey("pin_salt_hex")
    private val keyHash = stringPreferencesKey("pin_hash_hex")

    override suspend fun isSuperUserPinConfigured(): Boolean {
        val prefs = appContext.authDataStore.data.first()
        return prefs[keyHash] != null
    }

    override suspend fun setSuperUserPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hash = sha256(salt + pin.toByteArray())
        appContext.authDataStore.edit { prefs ->
            prefs[keySalt] = salt.toHex()
            prefs[keyHash] = hash.toHex()
        }
    }

    override suspend fun verifySuperUserPin(pin: String): Boolean {
        val prefs = appContext.authDataStore.data.first()
        val saltHex = prefs[keySalt] ?: return false
        val expectedHash = prefs[keyHash] ?: return false
        val actualHash = sha256(saltHex.fromHex() + pin.toByteArray()).toHex()
        return actualHash == expectedHash
    }

    override fun enterSuperUserMode() {
        _role.value = UserRole.SUPER_USER
    }

    override fun exitSuperUserMode() {
        _role.value = UserRole.OPERATOR
    }

    override suspend fun clearSuperUserPin() {
        appContext.authDataStore.edit { it.clear() }
        _role.value = UserRole.OPERATOR
    }

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
