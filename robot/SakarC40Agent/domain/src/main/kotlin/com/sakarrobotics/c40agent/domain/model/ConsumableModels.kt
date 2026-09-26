package com.sakarrobotics.c40agent.domain.model

enum class ConsumableType {
    SIDE_BRUSH, SWEEPING_BRUSH, FIBRE_BRUSH, WASHING_BRUSH, DUST_MOP_BRUSH,
    SQUEEGEE_BLADE, HEPA_FILTER, DUST_BAG
}

/**
 * Operator-tracked consumable wear. [usedHours] is accumulated locally by
 * this app (from cleaning-session runtime, itself SIMULATED - see
 * CleaningSession) or manually adjusted by an operator; it is NOT read
 * from any wear sensor, because the vendored Peanut SDK exposes none (see
 * COMPATIBILITY_REPORT.md). Never present [usedHours]/[remainingHours] as
 * sensor-confirmed data in the UI - always label it "operator-tracked".
 */
data class ConsumableItem(
    val id: String,
    val type: ConsumableType,
    val displayName: String,
    val lifespanHours: Int,
    val usedHours: Int
) {
    val remainingHours: Int get() = (lifespanHours - usedHours).coerceAtLeast(0)
    val remainingFraction: Float get() = if (lifespanHours <= 0) 0f else remainingHours.toFloat() / lifespanHours
    val isReplacementDue: Boolean get() = remainingFraction <= 0.05f
}
