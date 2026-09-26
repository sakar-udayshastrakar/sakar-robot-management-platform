package com.sakarrobotics.c40agent.domain.model

enum class ScheduleRepeat { ONCE, DAILY, WEEKLY_CUSTOM }

enum class Weekday { MON, TUE, WED, THU, FRI, SAT, SUN }

/**
 * A locally-persisted (Room) scheduled cleaning task. Designed so a future
 * Sakar Cloud sync layer can push/pull this shape without a schema
 * change: [remoteId]/[lastSyncedAtMillis] are already present and unused
 * until that backend integration exists (see [SyncState.NOT_SYNCED] which
 * every schedule starts in).
 */
data class ScheduleTask(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val startTime: String, // "HH:mm"
    val endTime: String,   // "HH:mm"
    val repeat: ScheduleRepeat,
    val days: Set<Weekday>,
    val cleaningCycles: Int,
    val zoneIds: List<String>,
    val mode: CleaningMode,
    val intensity: CleaningIntensity,
    val remoteId: String? = null,
    val syncState: SyncState = SyncState.NOT_SYNCED
)

enum class SyncState { NOT_SYNCED, SYNCING, SYNCED, SYNC_FAILED }
