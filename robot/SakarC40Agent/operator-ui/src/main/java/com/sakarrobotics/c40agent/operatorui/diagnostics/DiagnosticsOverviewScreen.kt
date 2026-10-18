package com.sakarrobotics.c40agent.operatorui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.diagnostics.CapabilityCatalog
import com.sakarrobotics.c40agent.domain.diagnostics.SupportReportBuilder
import com.sakarrobotics.c40agent.domain.diagnostics.SupportReportInput
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.BatteryState
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.domain.model.CloudSyncState
import com.sakarrobotics.c40agent.domain.model.ConnectionDisplayState
import com.sakarrobotics.c40agent.domain.model.DiagnosticsSnapshot
import com.sakarrobotics.c40agent.domain.model.LogLevel
import com.sakarrobotics.c40agent.domain.model.LogRecord
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.domain.model.SensorReadings
import com.sakarrobotics.c40agent.domain.model.batteryReadout
import com.sakarrobotics.c40agent.domain.model.deriveConnectionDisplay
import com.sakarrobotics.c40agent.domain.model.displayName
import com.sakarrobotics.c40agent.domain.model.formatAge
import com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge
import com.sakarrobotics.c40agent.operatorui.common.CapabilityRow
import com.sakarrobotics.c40agent.operatorui.common.ConnectionStateRow
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Read-only diagnostics overview. Nothing here sends a command to the robot: the runtime, battery
 * and sensor sections only issue the same read-only SDK queries the app already uses, and only when
 * the operator presses the matching button - there is no background polling of the robot.
 */
class DiagnosticsOverviewViewModel(private val container: AppContainer) : ViewModel() {

    val link: StateFlow<RobotLinkState> = container.connectionRepository.linkState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RobotLinkState.DISCONNECTED)
    val lastCommunicationMillis: StateFlow<Long?> = container.connectionRepository.lastCommunicationMillis
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cloudSync: StateFlow<CloudSyncState> = container.connectionRepository.cloudSyncState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CloudSyncState.OFFLINE)
    val battery: StateFlow<BatteryState> = container.batteryRepository.battery
        .stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5000),
            BatteryState(null, null, null, null, Capability.UNKNOWN)
        )

    var snapshot by mutableStateOf<DiagnosticsSnapshot?>(null)
        private set
    var sensors by mutableStateOf<SensorReadings?>(null)
        private set
    var recentEvents by mutableStateOf<List<LogRecord>>(emptyList())
        private set
    var busy by mutableStateOf<String?>(null)
        private set

    private var appVersion: String? = null
    private var sdkVersion: String? = null
    private var androidVersion: String? = null

    init {
        refreshEvents()
        viewModelScope.launch {
            // Local build/environment strings only - no robot request.
            runCatching { container.identityRepository.identity() }.onSuccess {
                appVersion = it.appVersionName
                sdkVersion = it.sdkVersion
                androidVersion = it.androidVersion
            }
        }
    }

    fun readRuntime() = guarded("runtime") { snapshot = container.diagnosticsRepository.snapshot() }

    fun readBattery() = guarded("battery") { container.batteryRepository.refresh() }

    fun readSensors() = guarded("sensors") { sensors = container.sensorsRepository.readSensors() }

    fun refreshEvents() {
        recentEvents = container.logsRepository.tail(EVENT_SCAN_LIMIT)
            .filter { it.level != LogLevel.INFO }
            .takeLast(MAX_EVENTS_SHOWN)
            .reversed()
    }

    fun buildReport(): String {
        val lastComm = lastCommunicationMillis.value
        val linkState = link.value
        return SupportReportBuilder.build(
            SupportReportInput(
                generatedAtMillis = System.currentTimeMillis(),
                appVersion = appVersion,
                sdkVersion = sdkVersion,
                androidVersion = androidVersion,
                link = linkState,
                connection = deriveConnectionDisplay(linkState, lastComm),
                lastCommunicationMillis = lastComm,
                battery = battery.value,
                diagnostics = snapshot,
                recentEvents = recentEvents
            )
        )
    }

    private fun guarded(what: String, block: suspend () -> Unit) {
        if (busy != null) return
        busy = what
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                container.eventLogger.error("Diagnostics", "Reading $what failed: ${e.message}")
            } finally {
                busy = null
                refreshEvents()
            }
        }
    }

    private companion object {
        const val EVENT_SCAN_LIMIT = 400
        const val MAX_EVENTS_SHOWN = 10
    }
}

@Composable
fun DiagnosticsOverviewScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: DiagnosticsOverviewViewModel =
        viewModel(factory = viewModelFactory { initializer { DiagnosticsOverviewViewModel(container) } })

    val link by viewModel.link.collectAsState()
    val lastComm by viewModel.lastCommunicationMillis.collectAsState()
    val cloud by viewModel.cloudSync.collectAsState()
    val battery by viewModel.battery.collectAsState()

    // Re-render the "x ago" text periodically; reads local state only.
    var nowMillis by androidx.compose.runtime.remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            nowMillis = System.currentTimeMillis()
        }
    }

    DiagnosticsOverviewContent(
        state = DiagnosticsOverviewState(
            link = link,
            lastCommunicationMillis = lastComm,
            cloud = cloud,
            battery = battery,
            snapshot = viewModel.snapshot,
            sensors = viewModel.sensors,
            recentEvents = viewModel.recentEvents,
            busy = viewModel.busy,
            nowMillis = nowMillis
        ),
        onBack = onBack,
        onReadBattery = viewModel::readBattery,
        onReadRuntime = viewModel::readRuntime,
        onReadSensors = viewModel::readSensors,
        onRefreshEvents = viewModel::refreshEvents,
        onCopyReport = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Sakar support report", viewModel.buildReport()))
            Toast.makeText(context, "Support report copied", Toast.LENGTH_SHORT).show()
        }
    )
}

/** Everything the Diagnostics content displays, so it can be rendered (and UI-tested) without an AppContainer. */
data class DiagnosticsOverviewState(
    val link: RobotLinkState,
    val lastCommunicationMillis: Long?,
    val cloud: CloudSyncState,
    val battery: BatteryState,
    val snapshot: DiagnosticsSnapshot?,
    val sensors: SensorReadings?,
    val recentEvents: List<LogRecord>,
    /** Which read is in flight ("battery" / "runtime" / "sensors"), or null. */
    val busy: String?,
    val nowMillis: Long
)

@Composable
fun DiagnosticsOverviewContent(
    state: DiagnosticsOverviewState,
    onBack: () -> Unit,
    onReadBattery: () -> Unit,
    onReadRuntime: () -> Unit,
    onReadSensors: () -> Unit,
    onRefreshEvents: () -> Unit,
    onCopyReport: () -> Unit
) {
    val link = state.link
    val lastComm = state.lastCommunicationMillis
    val cloud = state.cloud
    val battery = state.battery
    val nowMillis = state.nowMillis
    val busy = state.busy
    val connection = deriveConnectionDisplay(link, lastComm)

    Scaffold(topBar = { SakarTopBar("Diagnostics", onBack) }, containerColor = SakarColors.Background) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(ScreenPadding).testTag(TAG_LIST),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                InlineBanner(
                    "Read-only. Nothing on this screen sends a command to the robot. Data values carry a badge: LIVE (the SDK answered), SIMULATED, UNKNOWN (unverified or not read yet) or NOT AVAILABLE. Connection state is shown separately with a STATE chip.",
                    isWarning = false
                )
            }
            item {
                SectionCard("Connection", modifier = Modifier.testTag(TAG_CONNECTION)) {
                    // Connection STATE rows carry a state chip, never a LIVE/data badge: "is the data real"
                    // and "is the robot connected" are separate questions (see ConnectionStateChip).
                    ConnectionStateRow("Robot connection", connection.label(), connection)
                    CapabilityRow("SDK link", link.displayName(), capability = null)
                    Text(
                        "Source: the in-process SDK controller state, read locally. It does not prove the robot is responding.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SakarColors.TextFaint
                    )
                    CapabilityRow(
                        "Last successful SDK exchange",
                        lastComm?.let { formatAge(it, nowMillis) } ?: "None this session",
                        // A past exchange is not "live": the LIVE badge is only shown while ONLINE.
                        when {
                            lastComm == null -> Capability.UNKNOWN
                            connection == ConnectionDisplayState.ONLINE -> Capability.REAL
                            else -> null
                        }
                    )
                    CapabilityRow(
                        "Sakar Cloud",
                        if (cloud == CloudSyncState.ONLINE) "Online" else "Offline - no cloud client is wired",
                        Capability.UNAVAILABLE
                    )
                }
            }
            item {
                SectionCard("Battery") {
                    CapabilityRow("Level", batteryReadout(battery), battery.capability)
                    val raw = battery.raw
                    Text(
                        when {
                            raw == null -> "No battery response yet."
                            battery.capability == Capability.UNAVAILABLE -> "Query failed: $raw"
                            else -> "Raw response (not decoded): $raw"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = SakarColors.TextFaint
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReadBattery, enabled = busy == null) {
                        Text(if (busy == "battery") "Reading..." else "Query battery")
                    }
                }
            }
            item {
                SectionCard("Runtime info") {
                    val snap = state.snapshot
                    if (snap == null) {
                        CapabilityRow("Runtime snapshot", "Not read yet", Capability.UNKNOWN)
                    } else if (snap.capability == Capability.UNAVAILABLE) {
                        CapabilityRow("Runtime snapshot", "Not available - SDK link is not connected", Capability.UNAVAILABLE)
                    } else {
                        CapabilityRow("Work mode", snap.workMode.toString(), snap.capability)
                        CapabilityRow("Power", snap.power.toString(), snap.capability)
                        CapabilityRow("Odometer", snap.totalOdometer?.toString() ?: "-", snap.capability)
                        CapabilityRow(
                            "Emergency stop",
                            "engaged: ${snap.emergencyStopEngaged}, enabled: ${snap.emergencyEnabled}",
                            snap.capability
                        )
                        CapabilityRow("Motor status code", snap.motorStatusCode.toString(), snap.capability)
                        CapabilityRow("Last health event", snap.lastHealthEvent ?: "none received", snap.capability)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReadRuntime, enabled = busy == null) {
                        Text(if (busy == "runtime") "Reading..." else "Read runtime info")
                    }
                }
            }
            item {
                SectionCard("Sensors", modifier = Modifier.testTag(TAG_SENSORS)) {
                    val readings = state.sensors
                    if (readings == null) {
                        CapabilityRow("Sensor readings", "Not read yet", Capability.UNKNOWN)
                    } else {
                        listOf(
                            "LiDAR" to readings.lidar,
                            "Depth camera" to readings.depth,
                            "Sonar" to readings.sonar,
                            "IMU" to readings.imu
                        ).forEach { (label, rated) ->
                            CapabilityRow(label, rated.value.take(MAX_SENSOR_CHARS), rated.capability)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onReadSensors, enabled = busy == null) {
                        Text(if (busy == "sensors") "Reading..." else "Read sensors")
                    }
                }
            }
            item {
                SectionCard("What is real in this build") {
                    CapabilityCatalog.entries.forEach { entry ->
                        Row(
                            Modifier.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                                Text(entry.name, style = MaterialTheme.typography.titleSmall, color = SakarColors.TextPrimary)
                                Text(entry.note, style = MaterialTheme.typography.bodySmall, color = SakarColors.TextFaint)
                            }
                            CapabilityBadge(entry.capability)
                        }
                    }
                }
            }
            item {
                SectionCard("Recent warnings and errors") {
                    val events = state.recentEvents
                    if (events.isEmpty()) {
                        EmptyState("No warnings or errors recorded this session")
                    } else {
                        events.forEach { event ->
                            Text(
                                "${formatClock(event.timestampMillis)}  ${event.level}  [${event.tag}]",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (event.level == LogLevel.ERROR) SakarColors.Danger else SakarColors.WarningText
                            )
                            Text(
                                event.message.take(MAX_EVENT_CHARS),
                                style = MaterialTheme.typography.bodySmall,
                                color = SakarColors.TextMuted,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }
                    }
                    OutlinedButton(onClick = onRefreshEvents) { Text("Refresh") }
                }
            }
            item {
                SectionCard("Support report") {
                    Text(
                        "Copies a plain-text summary to the clipboard. Secrets are redacted and the robot's IP address is left out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SakarColors.TextFaint
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = onCopyReport) { Text("Copy support report") }
                }
            }
        }
    }
}

private fun ConnectionDisplayState.label(): String = when (this) {
    ConnectionDisplayState.ONLINE -> "Online"
    ConnectionDisplayState.OFFLINE -> "Offline"
    ConnectionDisplayState.CONNECTING -> "Connecting"
    ConnectionDisplayState.UNKNOWN -> "Unknown - no successful SDK exchange yet"
}

private fun formatClock(millis: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(millis))

/** Test tags (used by the instrumentation tests in src/androidTest). */
const val TAG_LIST = "diagnostics_list"
const val TAG_CONNECTION = "diagnostics_connection"
const val TAG_SENSORS = "diagnostics_sensors"

private const val MAX_SENSOR_CHARS = 160
private const val MAX_EVENT_CHARS = 300
