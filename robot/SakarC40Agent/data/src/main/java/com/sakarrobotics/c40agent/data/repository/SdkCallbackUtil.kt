package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.sdk.SdkCallback
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridges the Java-callback-based [SdkCallback] surface (C40RobotController,
 * NavigationBridge, ChargingBridge, PeanutSdkBridge) into a Kotlin
 * `suspend` call returning [Result], so every data-layer repository in
 * this package can be written as plain suspend functions instead of
 * nested callbacks.
 */
suspend fun awaitSdkCall(block: (SdkCallback) -> Unit): Result<String> =
    suspendCancellableCoroutine { cont ->
        block(object : SdkCallback {
            override fun onSuccess(rawResponse: String?) {
                if (cont.isActive) cont.resume(Result.success(rawResponse.orEmpty()))
            }

            override fun onError(errorCode: Int, errorMessage: String?) {
                if (cont.isActive) {
                    cont.resume(Result.failure(SdkCallException(errorCode, errorMessage.orEmpty())))
                }
            }
        })
    }

class SdkCallException(val errorCode: Int, val errorText: String) : Exception("SDK error $errorCode: $errorText")

/** [com.sakarrobotics.c40agent.robot.C40RobotController.ERROR_BLOCKED_BY_OPERATING_MODE]. */
const val ERROR_BLOCKED_BY_OPERATING_MODE = -1001
