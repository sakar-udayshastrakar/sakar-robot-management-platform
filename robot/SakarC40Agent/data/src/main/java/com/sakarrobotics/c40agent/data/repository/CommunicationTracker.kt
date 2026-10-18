package com.sakarrobotics.c40agent.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Records evidence that the app really exchanged data with the robot SDK: the timestamp of the most
 * recent *successful* call. Failures never update it, so a connection that has gone quiet or started
 * failing cannot keep looking fresh. Shared by every SDK-facing repository via [call].
 */
class CommunicationTracker(private val clock: () -> Long = System::currentTimeMillis) {

    private val _lastSuccessMillis = MutableStateFlow<Long?>(null)
    val lastSuccessMillis: Flow<Long?> = _lastSuccessMillis.asStateFlow()

    val current: Long? get() = _lastSuccessMillis.value

    fun markSuccess() {
        _lastSuccessMillis.value = clock()
    }

    /** Runs an SDK call and records it as communication evidence only if it succeeded. */
    suspend fun call(block: (com.sakarrobotics.c40agent.sdk.SdkCallback) -> Unit): Result<String> =
        awaitSdkCall(block).also { if (it.isSuccess) markSuccess() }
}
