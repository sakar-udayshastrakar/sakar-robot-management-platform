package com.sakarrobotics.installation.core.data

import com.sakarrobotics.installation.core.model.BackendInfo
import com.sakarrobotics.installation.core.model.DiagnosticCategory
import com.sakarrobotics.installation.core.model.DiagnosticItem
import com.sakarrobotics.installation.core.model.DiagnosticState
import com.sakarrobotics.installation.core.model.DiagnosticsRun
import com.sakarrobotics.installation.core.model.SimulationScenario
import com.sakarrobotics.installation.core.repository.DiagnosticsRepository
import com.sakarrobotics.installation.core.storage.Clock
import com.sakarrobotics.installation.core.storage.Json
import com.sakarrobotics.installation.core.storage.KeyValueStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Generated diagnostic content. Every message starts with "Simulated" on purpose: none of these
 * rows reflect a physical sensor, and the guidance is generic placeholder advice for review, not
 * a record of any check.
 */
internal object SimulatedDiagnosticsData {

    private fun item(
        id: String,
        category: DiagnosticCategory,
        title: String,
        state: DiagnosticState,
        message: String,
        vararg troubleshooting: String
    ) = DiagnosticItem(id, category, title, state, message, troubleshooting.toList())

    fun readiness(scenario: SimulationScenario): List<DiagnosticItem> {
        val degraded = scenario == SimulationScenario.DEGRADED
        return listOf(
            item(
                "ready-power", DiagnosticCategory.READINESS, "Power and battery",
                if (degraded) DiagnosticState.WARNING else DiagnosticState.OK,
                if (degraded) "Simulated: charge level below the recommended level for an installation session."
                else "Simulated: charge level is sufficient for an installation session.",
                "Charge the robot before a real installation session.",
                "Keep the charger accessible while mapping a large site."
            ),
            item(
                "ready-storage", DiagnosticCategory.READINESS, "Storage space",
                DiagnosticState.OK,
                "Simulated: enough free storage for map data.",
                "Remove unused maps if storage runs low."
            ),
            item(
                "ready-software", DiagnosticCategory.READINESS, "Software versions",
                DiagnosticState.OK,
                "Simulated: software versions are consistent.",
                "Confirm the supported software combination with Sakar support before a real installation."
            ),
            item(
                "ready-site", DiagnosticCategory.READINESS, "Site conditions",
                if (degraded) DiagnosticState.WARNING else DiagnosticState.OK,
                if (degraded) "Simulated: example warning - site access not yet confirmed."
                else "Simulated: site access confirmed.",
                "Confirm access to all areas, doors and lifts before scanning.",
                "Remove temporary obstacles from the route."
            )
        )
    }

    fun sensors(scenario: SimulationScenario): List<DiagnosticItem> {
        val degraded = scenario == SimulationScenario.DEGRADED
        return listOf(
            item(
                "sensor-lidar", DiagnosticCategory.SENSOR, "LiDAR",
                DiagnosticState.OK,
                "Simulated: scan data stream nominal (example result).",
                "If this ever reports a fault on real hardware: inspect the window for dirt or damage.",
                "Check that nothing blocks the scan plane."
            ),
            item(
                "sensor-depth", DiagnosticCategory.SENSOR, "Depth / stereo camera",
                if (degraded) DiagnosticState.WARNING else DiagnosticState.OK,
                if (degraded) "Simulated: example warning - reduced image quality."
                else "Simulated: image stream nominal (example result).",
                "Clean the camera lens.",
                "Avoid strong direct sunlight or reflective floors during scanning."
            ),
            item(
                "sensor-ultrasonic", DiagnosticCategory.SENSOR, "Ultrasonic sensors",
                if (degraded) DiagnosticState.WARNING else DiagnosticState.OK,
                if (degraded) "Simulated: example warning - one channel reports intermittent echo loss."
                else "Simulated: all channels nominal (example result).",
                "Check the sensor faces for dirt or obstruction.",
                "Re-run the diagnostics after cleaning."
            ),
            item(
                "sensor-imu", DiagnosticCategory.SENSOR, "IMU",
                DiagnosticState.OK,
                "Simulated: orientation data nominal (example result).",
                "Keep the robot stationary on a level surface when checking orientation."
            ),
            item(
                "sensor-localization", DiagnosticCategory.SENSOR, "Localization",
                if (degraded) DiagnosticState.WARNING else DiagnosticState.OK,
                if (degraded) "Simulated: example warning - no map loaded, so localization is not initialised."
                else "Simulated: localization ready (example result).",
                "Create and save a map for the floor, then re-run the diagnostics.",
                "Place the robot at a recognisable start position."
            ),
            item(
                "sensor-mapping", DiagnosticCategory.SENSOR, "Mapping service",
                if (degraded) DiagnosticState.FAILED else DiagnosticState.OK,
                if (degraded) "Simulated: example failure - the mapping service did not respond."
                else "Simulated: mapping service responding (example result).",
                "On real hardware this would require support-guided recovery.",
                "In this demo, switch the scenario back to Healthy and re-run the diagnostics."
            )
        )
    }
}

class SimulatedDiagnosticsRepository(
    private val store: KeyValueStore,
    private val clock: Clock,
    private val latencyMillis: Long = DEFAULT_LATENCY_MILLIS
) : DiagnosticsRepository {

    override val backend: BackendInfo = SimulatedBackend.info

    private val _scenario = MutableStateFlow(
        Json.load(store, SCENARIO_KEY) { SimulationScenario.HEALTHY }
    )
    override val scenario: StateFlow<SimulationScenario> = _scenario.asStateFlow()

    private val _latest = MutableStateFlow(Json.loadOrNull<DiagnosticsRun>(store, LATEST_KEY))
    override val latestSensors: StateFlow<DiagnosticsRun?> = _latest.asStateFlow()

    /** Reset demo data: healthy scenario, no remembered run. */
    fun resetAll() {
        _scenario.value = SimulationScenario.HEALTHY
        Json.save(store, SCENARIO_KEY, SimulationScenario.HEALTHY)
        _latest.value = null
        Json.save(store, LATEST_KEY, null)
    }

    override fun setScenario(scenario: SimulationScenario) {
        _scenario.value = scenario
        Json.save(store, SCENARIO_KEY, scenario)
    }

    override suspend fun runReadiness(): DiagnosticsRun {
        simulateLatency()
        val s = _scenario.value
        return DiagnosticsRun(clock.nowMillis(), s, SimulatedDiagnosticsData.readiness(s))
    }

    override suspend fun runSensors(): DiagnosticsRun {
        simulateLatency()
        val s = _scenario.value
        val run = DiagnosticsRun(clock.nowMillis(), s, SimulatedDiagnosticsData.sensors(s))
        _latest.value = run
        Json.save(store, LATEST_KEY, run)
        return run
    }

    private suspend fun simulateLatency() {
        if (latencyMillis > 0) delay(latencyMillis)
    }

    companion object {
        const val DEFAULT_LATENCY_MILLIS = 700L
        private const val SCENARIO_KEY = "diag.scenario.v1"
        private const val LATEST_KEY = "diag.latest.v1"
    }
}
