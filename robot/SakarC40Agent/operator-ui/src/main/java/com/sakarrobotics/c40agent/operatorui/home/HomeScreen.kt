package com.sakarrobotics.c40agent.operatorui.home

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.CleaningRunState
import com.sakarrobotics.c40agent.domain.model.RobotLinkState
import com.sakarrobotics.c40agent.operatorui.common.BigActionCard
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onOpenStartCleaning: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenManualDrive: () -> Unit,
    onOpenTeachRoute: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSuperUser: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: HomeViewModel = viewModel(factory = viewModelFactory { initializer { HomeViewModel(container) } })
    val state by viewModel.uiState.collectAsState()
    val message by viewModel.messages.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.connect() }
    LaunchedEffect(message) { if (message != null) viewModel.consumeMessage() }

    // Manual Push / Recharge / Mute / Light are UI-only placeholders for this checkpoint - none of
    // them call into the robot yet, per explicit instruction. A toast makes that honest instead of
    // a button that silently does nothing.
    val showPlaceholder: (String) -> Unit = { feature ->
        Toast.makeText(context, "$feature — not yet available", Toast.LENGTH_SHORT).show()
    }

    // Real cleaning progress, shown on the idle screensaver only while a session is actually
    // active - never fabricated, and omitted entirely (null caption) the rest of the time.
    val screensaverCaption = state.session?.takeIf { it.state != CleaningRunState.IDLE }
        ?.let { "${it.mode.name.replace('_', ' ')} · ${it.progressPercent}%" }

    IdleLockController(screensaverCaption = screensaverCaption) {
    Scaffold(containerColor = SakarColors.Background) { padding ->
        // BoxWithConstraints gives the card row a height that is a FIXED fraction of the actual
        // available screen height (measured directly from the reference screenshot: the card row
        // is 40.2% of total screen height), independent of how tall the header/status area happens
        // to be. The reference does NOT stretch its card grid to fill all remaining vertical space
        // - it leaves a sizeable blank band below the grid before the screen's bottom edge - so the
        // Column below is intentionally NOT weight-filled after the Row; whatever height is left
        // over simply stays blank, exactly like the reference.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            // 0.462, not 0.402: the reference's 40.2% was measured against its RAW screenshot
            // pixels (which include its own captured system status bar). This BoxWithConstraints'
            // maxHeight is the app's usable content area, which Scaffold has already shrunk by the
            // real system status/nav bar insets (measured on this device: usable/raw ~= 0.87), so
            // the fraction is scaled up to land on the same 40.2%-of-raw-screenshot target once the
            // system bars are added back in - verified against an actual device screenshot below.
            val cardRowHeight = maxHeight * 0.462f
            Column(modifier = Modifier.fillMaxSize()) {
                HomeTopBar(
                    linkState = state.linkState,
                    onManualPush = { showPlaceholder("Manual Push") },
                    onRecharge = { showPlaceholder("Recharge") },
                    onMute = { showPlaceholder("Mute") },
                    onLight = { showPlaceholder("Light") },
                    onOpenSettings = onOpenSettings,
                    onOpenSuperUser = onOpenSuperUser
                )
                Spacer(Modifier.height(18.dp))

                if (state.linkState != RobotLinkState.CONNECTED) {
                    StatusBubble(connectionBannerMessage(state.linkState))
                    Spacer(Modifier.height(16.dp))
                }

                if (state.session != null && state.session!!.state != CleaningRunState.IDLE) {
                    ActiveCleaningCard(state, onPause = viewModel::pauseCleaning, onResume = viewModel::resumeCleaning, onStop = viewModel::stopCleaning)
                    Spacer(Modifier.height(16.dp))
                }

                // Hero (robot illustration) on the left, primary quick actions on the right - same
                // split-panel composition and ~35/65 proportion measured from the reference layout
                // (reference robot column right edge to reference card grid left edge).
                Row(modifier = Modifier.fillMaxWidth().height(cardRowHeight)) {
                    RobotHeroPanel(
                        modifier = Modifier.weight(0.35f).fillMaxHeight(),
                        image = painterResource(com.sakarrobotics.c40agent.operatorui.R.drawable.sakar_cleanbot_hero)
                    )
                    Spacer(Modifier.width(24.dp))
                    QuickActionsGrid(
                        modifier = Modifier.weight(0.65f).fillMaxHeight(),
                        onOpenStartCleaning = onOpenStartCleaning,
                        onOpenSchedule = onOpenSchedule,
                        onOpenManualDrive = onOpenManualDrive,
                        onOpenTeachRoute = onOpenTeachRoute
                    )
                }
            }
        }
    }
    }
}

private fun connectionBannerMessage(linkState: RobotLinkState): String = when (linkState) {
    RobotLinkState.CONNECTING -> "Connecting to the robot…"
    RobotLinkState.INIT_FAILED -> "Not connected — the onboard SDK could not initialize"
    RobotLinkState.DISCONNECTED -> "Not connected"
    RobotLinkState.CONNECTED -> "" // unreachable: banner is only shown when not CONNECTED
}

/**
 * Top brand bar: flat (no card/shadow), matching the reference's minimal chrome. Sakar
 * mark/wordmark on the left - long-pressing it is the sole, reference-confirmed Super User entry
 * point (no visible "Super User" button anywhere, matching the reference exactly). On the right: a
 * compact status row (real robot-link indicator + live device clock - the runtime values that
 * correspond to the reference's own top status indicators), then the reference's 5-button toolbar
 * below it. Manual Push, Recharge, Mute and Light are UI-only placeholders for this checkpoint
 * (see [showPlaceholder] callers in [HomeScreen]) - only Settings is a real, already-existing
 * navigation action.
 */
@Composable
private fun HomeTopBar(
    linkState: RobotLinkState,
    onManualPush: () -> Unit,
    onRecharge: () -> Unit,
    onMute: () -> Unit,
    onLight: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSuperUser: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        // Only the Sakar Robotics wordmark - it already contains its own "R" mark, and no
        // subtitle/product name is shown alongside it per explicit instruction. Long-pressing it
        // is the sole entry point into Super User login
        // (SuperUserLoginScreen/AuthUseCases/DataStoreAuthRepository) - the reference has no
        // visible "Super User" button anywhere, so this is the only access path, matching it
        // exactly. No new credential system, no bypass.
        Image(
            painter = painterResource(com.sakarrobotics.c40agent.operatorui.R.drawable.sakar_robotics_wordmark),
            contentDescription = "Sakar Robotics",
            modifier = Modifier
                .height(32.dp)
                .pointerInput(onOpenSuperUser) { detectTapGestures(onLongPress = { onOpenSuperUser() }) }
        )
        Column(horizontalAlignment = Alignment.End) {
            // Reference has no visible "Super User" control anywhere in its top status area - the
            // long-press-logo gesture above is the sole, reference-confirmed entry point. Only the
            // runtime values that correspond to the reference's own top status indicators
            // (connectivity, clock) are kept here.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ConnectionIndicator(linkState)
                LiveClock()
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ToolbarChip(icon = Icons.Filled.PanTool, label = "Manual Push", onClick = onManualPush)
                ToolbarChip(icon = Icons.Filled.Bolt, label = "Recharge", onClick = onRecharge)
                ToolbarIconButton(icon = Icons.Filled.VolumeUp, contentDescription = "Mute", onClick = onMute)
                ToolbarIconButton(icon = Icons.Filled.Lightbulb, contentDescription = "Light", onClick = onLight)
                ToolbarIconButton(icon = Icons.Filled.Settings, contentDescription = "Robot Status & Settings", onClick = onOpenSettings)
            }
        }
    }
}

/** Real robot connection state, at a glance - never a fabricated "connected". */
@Composable
private fun ConnectionIndicator(linkState: RobotLinkState) {
    val (dotColor, label) = when (linkState) {
        RobotLinkState.CONNECTED -> SakarColors.Success to "Connected"
        RobotLinkState.CONNECTING -> SakarColors.Info to "Connecting…"
        RobotLinkState.INIT_FAILED -> SakarColors.Danger to "Offline"
        RobotLinkState.DISCONNECTED -> SakarColors.TextSubtle to "Offline"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(dotColor, CircleShape))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = dotColor, fontWeight = FontWeight.SemiBold)
    }
}

/** Real device time (not fabricated telemetry), refreshed once a minute - matches the reference's top-right clock. */
@Composable
private fun LiveClock() {
    var timeText by remember { mutableStateOf(formatNow()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            timeText = formatNow()
        }
    }
    Text(timeText, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextMuted)
}

private fun formatNow(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())

/** Solid filled pill with icon + label, matching the reference's filled toolbar buttons (Manual Push, Recharge) - sized for a tablet touch target. */
@Composable
private fun ToolbarChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(color = SakarColors.Primary, shape = RoundedCornerShape(28.dp), modifier = Modifier.height(56.dp), onClick = onClick) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = SakarColors.Surface, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = SakarColors.Surface)
        }
    }
}

/** Flat white circular icon-only button, matching the reference's secondary toolbar buttons (Mute, Light, Settings) - sized for a tablet touch target. */
@Composable
private fun ToolbarIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Surface(color = SakarColors.Surface, shape = CircleShape, modifier = Modifier.size(56.dp), onClick = onClick) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = SakarColors.TextMuted, modifier = Modifier.size(26.dp))
        }
    }
}

/** Compact, wrap-content warning pill for the current real anomaly (e.g. not connected) - same affordance/position as the reference's alert bubble, never a fabricated message. */
@Composable
private fun StatusBubble(message: String) {
    Surface(color = SakarColors.Warning.copy(alpha = 0.16f), shape = RoundedCornerShape(24.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("⚠", color = SakarColors.WarningText)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = SakarColors.WarningText, fontWeight = FontWeight.SemiBold)
            Text("›", color = SakarColors.WarningText)
        }
    }
}

/**
 * Robot illustration area - a dedicated, replaceable asset slot. [image] is the real Sakar
 * CleanBot5000Plus product photo (provided directly by the user, saved at
 * `operator-ui/src/main/res/drawable/sakar_cleanbot_hero.webp`, transparent background) - not a
 * Keenon image and not downloaded from the internet. When [image] is null this falls back to a
 * vector glyph instead, so the slot degrades gracefully if the asset is ever removed. The photo
 * has a real transparent background, so it sits on the same soft brand-colored glow used by the
 * fallback glyph, matching the reference's subtle blob-behind-the-robot composition.
 */
@Composable
private fun RobotHeroPanel(modifier: Modifier = Modifier, image: Painter? = null) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.fillMaxHeight(0.85f).aspectRatio(1f).background(SakarColors.Primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (image != null) {
                Image(painter = image, contentDescription = "Sakar CleanBot5000Plus", modifier = Modifier.fillMaxSize(0.8f))
            } else {
                Icon(Icons.Filled.SmartToy, contentDescription = null, tint = SakarColors.Primary, modifier = Modifier.fillMaxSize(0.55f))
            }
        }
    }
}

/**
 * Reference's 3-column geometry, not a 4-equal-card row: Start Cleaning and Scheduled Cleaning
 * are full-height columns; the third column stacks Teaching Mode above Manual Drive, each half
 * the height (minus half the gap) so the stacked pair's combined height matches the first two
 * columns exactly - all three columns share the Row's full height, which keeps their top edges
 * and bottom edges aligned on the same baseline. Gap (16dp) measured from the reference's
 * inter-card gap. Start Cleaning/Scheduled Cleaning show a small, bottom-left, thin-line icon
 * (titleFirst + iconAlignment = Start) matching the reference; Teaching Mode/Manual Drive have
 * NO icon at all in the reference (just a centered title), measured directly from the reference
 * screenshot - so those two omit the icon entirely rather than guessing a smaller one.
 */
@Composable
private fun QuickActionsGrid(
    modifier: Modifier = Modifier,
    onOpenStartCleaning: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenManualDrive: () -> Unit,
    onOpenTeachRoute: () -> Unit
) {
    val bigTitleStyle = MaterialTheme.typography.headlineSmall

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        BigActionCard(
            title = "Start\nCleaning",
            icon = { ActionIcon(Icons.Outlined.CleaningServices, size = 40.dp) },
            height = Dp.Unspecified,
            contentPadding = 24.dp,
            titleStyle = bigTitleStyle,
            horizontalAlignment = Alignment.CenterHorizontally,
            iconAlignment = Alignment.Start,
            titleFirst = true,
            onClick = onOpenStartCleaning,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        BigActionCard(
            title = "Scheduled\nCleaning",
            icon = { ActionIcon(Icons.Outlined.CalendarMonth, size = 40.dp) },
            height = Dp.Unspecified,
            contentPadding = 24.dp,
            titleStyle = bigTitleStyle,
            horizontalAlignment = Alignment.CenterHorizontally,
            iconAlignment = Alignment.Start,
            titleFirst = true,
            onClick = onOpenSchedule,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BigActionCard(
                title = "Teaching\nMode",
                icon = null,
                height = Dp.Unspecified,
                contentPadding = 18.dp,
                titleStyle = bigTitleStyle,
                horizontalAlignment = Alignment.CenterHorizontally,
                onClick = onOpenTeachRoute,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            BigActionCard(
                title = "Manual\nDrive",
                icon = null,
                height = Dp.Unspecified,
                contentPadding = 18.dp,
                titleStyle = bigTitleStyle,
                horizontalAlignment = Alignment.CenterHorizontally,
                onClick = onOpenManualDrive,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}

/** Plain standalone line icon, matching the reference - no circular chip behind it. */
@Composable
private fun ActionIcon(icon: ImageVector, size: Dp = 48.dp) {
    Icon(icon, contentDescription = null, tint = SakarColors.Primary, modifier = Modifier.size(size))
}

@Composable
private fun ActiveCleaningCard(
    state: HomeUiState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit
) {
    val session = state.session ?: return
    SectionCard(title = "Cleaning in progress — ${session.mode.name.replace('_', ' ')}") {
        Text("${session.progressPercent}% complete · cycle ${session.cyclesCompleted + 1} of ${session.cyclesRequested}", color = SakarColors.TextMuted)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (session.state == CleaningRunState.CLEANING) {
                ActionIconButton(Icons.Filled.PauseCircle, "Pause", onPause)
            } else if (session.state == CleaningRunState.PAUSED) {
                ActionIconButton(Icons.Filled.PlayCircle, "Resume", onResume)
            }
            ActionIconButton(Icons.Filled.StopCircle, "Stop", onStop)
        }
        com.sakarrobotics.c40agent.operatorui.common.CapabilityBadge(session.capability, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ActionIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        androidx.compose.material3.TextButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = SakarColors.Primary)
            Text(label, color = SakarColors.Primary)
        }
    }
}
