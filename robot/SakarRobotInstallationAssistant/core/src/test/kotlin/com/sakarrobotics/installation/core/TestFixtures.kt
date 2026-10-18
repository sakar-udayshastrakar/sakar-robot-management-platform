package com.sakarrobotics.installation.core

import com.sakarrobotics.installation.core.data.SimulatedMappingRepository
import com.sakarrobotics.installation.core.model.MappingPhase
import com.sakarrobotics.installation.core.storage.Clock
import com.sakarrobotics.installation.core.storage.InMemoryKeyValueStore
import com.sakarrobotics.installation.core.storage.KeyValueStore
import com.sakarrobotics.installation.core.storage.SequentialIdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

class FakeClock(var now: Long = 1_700_000_000_000L) : Clock {
    override fun nowMillis(): Long = now
}

/** Services wired for tests: manual mapping ticks (no timer) and no simulated latency. */
class Fixture(
    val store: KeyValueStore = InMemoryKeyValueStore(),
    val clock: FakeClock = FakeClock(),
    scope: CoroutineScope = CoroutineScope(Job()),
    tickMillis: Long = 0L
) {
    val services = AssistantServices(
        store = store,
        scope = scope,
        clock = clock,
        ids = SequentialIdGenerator(),
        mappingTickMillis = tickMillis,
        diagnosticsLatencyMillis = 0L
    )
    val mapping get() = services.mapping as SimulatedMappingRepository
    val floors get() = services.floors
    val diagnostics get() = services.diagnostics
    val installations get() = services.installations
    val reports get() = services.reports

    /** Runs a whole simulated scan to completion and saves it under [name] on [floorId]. */
    fun mapFloor(floorId: String, name: String, fullCoverage: Boolean = true) {
        check(mapping.startSession(floorId).isSuccess)
        if (fullCoverage) {
            while (mapping.session.value.phase == MappingPhase.MAPPING) mapping.advance(1)
        } else {
            while (mapping.session.value.progressPercent < SimulatedMappingRepository.MIN_FINISH_PERCENT) mapping.advance(1)
            check(mapping.finish().isSuccess)
        }
        check(mapping.saveMap(name).isSuccess) { "save failed" }
    }
}
