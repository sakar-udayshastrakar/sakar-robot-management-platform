package com.sakarrobotics.c40agent.data.repository

import com.sakarrobotics.c40agent.domain.model.ActuatorTestResult
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.MapListResult
import com.sakarrobotics.c40agent.domain.model.MapSummary
import com.sakarrobotics.c40agent.domain.model.Rated

/**
 * Pure SDK-result -> domain mappings, kept free of Android/SDK types so the honesty rules are unit
 * testable: a failed or empty SDK read is never labelled REAL, and unverified payloads are never
 * parsed into typed values.
 */

/** Success with data -> REAL raw reading; failure -> UNAVAILABLE with the reason; empty success -> UNKNOWN. */
fun Result<String>.toRatedReading(): Rated<String> = fold(
    onSuccess = { raw ->
        if (raw.isBlank()) Rated("No data returned", Capability.UNKNOWN) else Rated(raw, Capability.REAL)
    },
    onFailure = { Rated("error: ${it.message}", Capability.UNAVAILABLE) }
)

/**
 * The battery payload shape is unverified on hardware, so percentage / charging / temperature stay
 * null and the capability is UNKNOWN even when the SDK answered; the raw answer is kept verbatim.
 */
fun Result<String>.toBatteryState(): BatteryState = fold(
    onSuccess = { raw -> BatteryState(null, null, null, raw, Capability.UNKNOWN) },
    onFailure = { BatteryState(null, null, null, it.message, Capability.UNAVAILABLE) }
)

fun initialBatteryState() = BatteryState(null, null, null, null, Capability.UNKNOWN)

/** Map-info response shape is unverified: name/inUse/size are not inferred, only the raw text is carried. */
fun Result<String>.toMapListResult(): MapListResult = fold(
    onSuccess = { raw ->
        if (raw.isBlank()) {
            MapListResult(emptyList(), "The robot returned no map information.")
        } else {
            MapListResult(
                listOf(
                    MapSummary(
                        id = "current",
                        name = "Robot map info",
                        inUse = null,
                        sizeBytes = null,
                        capability = Capability.UNKNOWN,
                        rawInfo = raw
                    )
                ),
                error = null
            )
        }
    },
    onFailure = { MapListResult(emptyList(), it.message ?: "Map info request failed") }
)

fun Result<String>.toActuatorTestResult(testId: String, nowMillis: Long) = fold(
    onSuccess = { ActuatorTestResult(testId, success = true, message = it, timestampMillis = nowMillis) },
    onFailure = { ActuatorTestResult(testId, success = false, message = it.message ?: "failed", timestampMillis = nowMillis) }
)
