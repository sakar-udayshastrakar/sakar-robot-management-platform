package com.sakarrobotics.c40agent.operatorui.manualdrive

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.model.CleaningIntensity
import com.sakarrobotics.c40agent.domain.model.CleaningMode
import com.sakarrobotics.c40agent.domain.model.ManualDriveBackendMode
import com.sakarrobotics.c40agent.domain.model.ManualDriveDirection
import com.sakarrobotics.c40agent.domain.model.SimulatedMovementState
import com.sakarrobotics.c40agent.domain.model.SimulatedRobotState
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Reference-matched mode/power selection screen, plus a separate Movement Controls dialog opened
 * from the top-bar icon button. The mode-card grid, power row and "Start Cleaning" button below
 * remain UI + local state only - they never call jog()/forward()/backward()/turnLeft()/
 * turnRight()/moveControl() or any other MotorComponent/PeanutSDK entry point, and "Start
 * Cleaning" does not start a real cleaning operation (the vendored SDK has no CleanComponent at
 * all - see CleaningRepository). Selecting a cleaning mode/intensity never controls physical
 * cleaning hardware. The Movement Controls dialog is the only part of this screen that sends real
 * MotorComponent commands (see ManualDriveViewModel/RobotNavigationRepositoryImpl.jog) and is kept
 * fully separate from the mode grid so neither is repurposed as the other.
 */
@Composable
fun ManualDriveScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedMode by remember { mutableStateOf(CleaningMode.WATER_SUCTION) }
    var selectedIntensity by remember { mutableStateOf(CleaningIntensity.STANDARD) }
    var showMovementControls by remember { mutableStateOf(false) }

    // Matches the reference's exact enabled/disabled states for this checkpoint - not a
    // capability lookup, just the fixed visual state the reference screenshot shows.
    val disabledModes = remember { setOf(CleaningMode.SWEEP_MOP, CleaningMode.SWEEP_VACUUM) }
    val modeOrder = remember {
        listOf(
            CleaningMode.SWEEP_MOP,
            CleaningMode.WATER_SUCTION,
            CleaningMode.SWEEP_VACUUM,
            CleaningMode.SWEEP_PUSH,
            CleaningMode.SWEEP
        )
    }

    Box(Modifier.fillMaxSize().background(SakarColors.Background)) {
        Column(Modifier.fillMaxSize().padding(ScreenPadding)) {
            Box(Modifier.fillMaxWidth()) {
                Surface(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.CenterStart).size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SakarColors.Surface,
                    shadowElevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SakarColors.TextPrimary)
                    }
                }
                Text(
                    "Manual Drive",
                    style = MaterialTheme.typography.headlineMedium,
                    color = SakarColors.TextPrimary,
                    modifier = Modifier.align(Alignment.Center)
                )
                Surface(
                    onClick = { showMovementControls = true },
                    modifier = Modifier.align(Alignment.CenterEnd).size(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SakarColors.Surface,
                    shadowElevation = 2.dp
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.OpenWith, contentDescription = "Movement Controls", tint = SakarColors.TextPrimary)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(28.dp),
                color = SakarColors.Surface,
                shadowElevation = 1.dp
            ) {
                Column(Modifier.fillMaxSize().padding(28.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        modeOrder.forEach { mode ->
                            DriveModeCard(
                                title = mode.displayName(),
                                glyph = mode.glyph(),
                                selected = selectedMode == mode,
                                enabled = mode !in disabledModes,
                                onClick = { selectedMode = mode },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        CleaningIntensity.values().forEach { intensity ->
                            PowerButton(
                                label = intensity.displayName(),
                                selected = selectedIntensity == intensity,
                                onClick = { selectedIntensity = intensity },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            Surface(
                onClick = { Toast.makeText(context, "Start Cleaning — not yet available", Toast.LENGTH_SHORT).show() },
                modifier = Modifier.fillMaxWidth().height(76.dp),
                shape = RoundedCornerShape(24.dp),
                color = SakarColors.Primary
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Start Cleaning", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }

    if (showMovementControls) {
        MovementControlsDialog(onDismiss = { showMovementControls = false })
    }
}

/**
 * Real robot movement, fully separate from the cleaning-mode UI above. Press-and-hold: each press
 * sends exactly one verified MotorComponent command (forward/backward/turnLeft/turnRight) and
 * release always sends STOP - see ManualDriveViewModel for why commands are not repeated while
 * held. Dismissing the dialog (any way - button or system back) also issues STOP.
 *
 * Manual Drive stays disabled unless C40RobotController's OperatingMode is HARDWARE_TEST, which
 * nothing in this build ever sets - every button below will show the resulting "Blocked: operating
 * mode is DIAGNOSTIC_ONLY" error verbatim rather than pretending to have moved the robot. This is
 * the intended, honest emulator/default behavior, not a bug.
 */
@Composable
private fun MovementControlsDialog(onDismiss: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ManualDriveViewModel = viewModel(factory = viewModelFactory { initializer { ManualDriveViewModel(container) } })
    val statusMessage by viewModel.statusMessage.collectAsState()
    val simulated = viewModel.backendMode == ManualDriveBackendMode.SIMULATED
    val simState by viewModel.simulatedState.collectAsState()

    DisposableEffect(Unit) {
        onDispose { viewModel.onLeaveManualDrive() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = SakarColors.Surface,
            shadowElevation = 4.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.fillMaxWidth()) {
                    Text(
                        "Movement Controls",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = SakarColors.TextPrimary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    Surface(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd).size(36.dp),
                        shape = CircleShape,
                        color = SakarColors.Background
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = SakarColors.TextPrimary, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (simulated) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = SakarColors.WarningText
                    ) {
                        Text(
                            "SIMULATION MODE — no real hardware is being driven",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                DirectionPad(
                    onPress = { direction -> viewModel.onPress(direction) },
                    onRelease = { viewModel.onRelease() }
                )

                Spacer(Modifier.height(16.dp))

                Surface(
                    onClick = { viewModel.onPress(ManualDriveDirection.STOP) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = SakarColors.Danger
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("STOP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    statusMessage ?: "Idle - hold a direction to drive",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (statusMessage != null) SakarColors.Danger else SakarColors.TextSubtle,
                    textAlign = TextAlign.Center
                )

                if (simulated) {
                    Spacer(Modifier.height(16.dp))
                    SimulationDiagnosticsPanel(simState)
                }
            }
        }
    }
}

/**
 * Live virtual-robot telemetry, SIMULATED backend only. Every value here comes from
 * SimulatedMotorController's own deterministic model - see its and SimulatedRobotState's KDoc for
 * why none of it represents real robot calibration or physics.
 */
@Composable
private fun SimulationDiagnosticsPanel(state: SimulatedRobotState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = SakarColors.Background
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            DiagnosticsRow("Status", if (state.movementState == SimulatedMovementState.MOVING) "MOVING" else "IDLE")
            DiagnosticsRow("Direction", state.lastCommand?.name ?: "—")
            DiagnosticsRow("Linear velocity", "%.2f m/s".format(state.linearVelocity))
            DiagnosticsRow("Angular velocity", "%.1f deg/s".format(state.angularVelocity))
            DiagnosticsRow("X", "%.2f m".format(state.x))
            DiagnosticsRow("Y", "%.2f m".format(state.y))
            DiagnosticsRow("Heading", "%.1f deg".format(state.headingDegrees))
            DiagnosticsRow("Encoder L", state.encoderLeft.toString())
            DiagnosticsRow("Encoder R", state.encoderRight.toString())
        }
    }
}

@Composable
private fun DiagnosticsRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SakarColors.TextSubtle)
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = SakarColors.TextPrimary)
    }
}

@Composable
private fun DirectionPad(
    onPress: (ManualDriveDirection) -> Unit,
    onRelease: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        DirectionButton(Icons.Filled.KeyboardArrowUp, "Forward", ManualDriveDirection.FORWARD, onPress, onRelease)
        Spacer(Modifier.height(10.dp))
        Row {
            DirectionButton(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Left", ManualDriveDirection.LEFT, onPress, onRelease)
            Spacer(Modifier.width(64.dp))
            DirectionButton(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Right", ManualDriveDirection.RIGHT, onPress, onRelease)
        }
        Spacer(Modifier.height(10.dp))
        DirectionButton(Icons.Filled.KeyboardArrowDown, "Reverse", ManualDriveDirection.REVERSE, onPress, onRelease)
    }
}

@Composable
private fun DirectionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    direction: ManualDriveDirection,
    onPress: (ManualDriveDirection) -> Unit,
    onRelease: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .pointerInput(direction) {
                detectTapGestures(
                    onPress = {
                        onPress(direction)
                        // Always STOP on release, whether the gesture completed normally or was
                        // cancelled (e.g. finger dragged off the button) - never leave the robot
                        // moving because a release event didn't fire cleanly.
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        shape = RoundedCornerShape(16.dp),
        color = SakarColors.PrimarySoft,
        border = BorderStroke(1.dp, SakarColors.Border)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = SakarColors.Primary, modifier = Modifier.size(32.dp))
        }
    }
}

private fun CleaningMode.displayName(): String = when (this) {
    CleaningMode.SWEEP -> "Sweep"
    CleaningMode.SWEEP_MOP -> "Sweep & Mop"
    CleaningMode.WATER_SUCTION -> "Water Suction"
    CleaningMode.SWEEP_VACUUM -> "Sweep & Vacuum"
    CleaningMode.SWEEP_PUSH -> "Sweep & Push"
}

private fun CleaningMode.glyph(): ModeGlyph = when (this) {
    CleaningMode.SWEEP -> ModeGlyph.SWEEP
    CleaningMode.SWEEP_MOP -> ModeGlyph.MOP
    CleaningMode.WATER_SUCTION -> ModeGlyph.WATER
    CleaningMode.SWEEP_VACUUM -> ModeGlyph.VACUUM
    CleaningMode.SWEEP_PUSH -> ModeGlyph.PUSH
}

/**
 * Original Sakar-drawn line-art glyphs for the five cleaning modes - not traced from or derived
 * from any vendor icon artwork. The reverse-engineering audit located the vendor's actual mode
 * icons (`mode_101`-`105`.webp) but explicitly could not resolve which icon maps to which named
 * mode (that binding happens in a RecyclerView adapter the audit didn't trace), so there was no
 * reliable ground truth to copy even if reuse were appropriate - these are new, simple geometric
 * marks designed to read clearly at 48-72dp on a tablet, one consistent stroke weight throughout.
 */
private enum class ModeGlyph { SWEEP, MOP, WATER, VACUUM, PUSH }

@Composable
private fun ModeIcon(glyph: ModeGlyph, tint: Color, size: Dp = 48.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.09f
        when (glyph) {
            ModeGlyph.SWEEP -> drawSweepGlyph(tint, stroke)
            ModeGlyph.MOP -> drawMopGlyph(tint, stroke)
            ModeGlyph.WATER -> drawWaterGlyph(tint, stroke)
            ModeGlyph.VACUUM -> drawVacuumGlyph(tint, stroke)
            ModeGlyph.PUSH -> drawPushGlyph(tint, stroke)
        }
    }
}

/** Fraction-of-canvas coordinate, since every glyph is designed in a normalized 0..1 square. */
private fun DrawScope.n(x: Float, y: Float): Offset = Offset(x * size.width, y * size.height)

private fun roundStroke(width: Float) = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round)

/** Broom: angled handle into a fanned bristle head with two binding/bristle lines. */
private fun DrawScope.drawSweepGlyph(tint: Color, strokeWidth: Float) {
    val stroke = roundStroke(strokeWidth)
    drawLine(tint, n(0.74f, 0.10f), n(0.42f, 0.60f), strokeWidth, StrokeCap.Round)
    val head = Path().apply {
        moveTo(n(0.42f, 0.60f).x, n(0.42f, 0.60f).y)
        lineTo(n(0.20f, 0.90f).x, n(0.20f, 0.90f).y)
        lineTo(n(0.64f, 0.90f).x, n(0.64f, 0.90f).y)
        close()
    }
    drawPath(head, tint, style = stroke)
    drawLine(tint, n(0.30f, 0.78f), n(0.56f, 0.78f), strokeWidth * 0.7f, StrokeCap.Round)
}

/** Mop: a bucket (trapezoid + rim) with a mop handle leaning in from the top-right. */
private fun DrawScope.drawMopGlyph(tint: Color, strokeWidth: Float) {
    val stroke = roundStroke(strokeWidth)
    val bucket = Path().apply {
        moveTo(n(0.26f, 0.52f).x, n(0.26f, 0.52f).y)
        lineTo(n(0.74f, 0.52f).x, n(0.74f, 0.52f).y)
        lineTo(n(0.66f, 0.90f).x, n(0.66f, 0.90f).y)
        lineTo(n(0.34f, 0.90f).x, n(0.34f, 0.90f).y)
        close()
    }
    drawPath(bucket, tint, style = stroke)
    // carry handle
    drawArc(
        color = tint,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = n(0.34f, 0.30f),
        size = androidx.compose.ui.geometry.Size((0.66f - 0.34f) * size.width, 0.44f * size.height),
        style = stroke
    )
    drawLine(tint, n(0.78f, 0.10f), n(0.58f, 0.52f), strokeWidth, StrokeCap.Round)
}

/** Water droplet with a small upward chevron inside suggesting suction/uptake. */
private fun DrawScope.drawWaterGlyph(tint: Color, strokeWidth: Float) {
    val stroke = roundStroke(strokeWidth)
    val drop = Path().apply {
        moveTo(n(0.5f, 0.10f).x, n(0.5f, 0.10f).y)
        cubicTo(n(0.5f, 0.10f).x, n(0.5f, 0.10f).y, n(0.82f, 0.52f).x, n(0.82f, 0.52f).y, n(0.82f, 0.66f).x, n(0.82f, 0.66f).y)
        cubicTo(n(0.82f, 0.83f).x, n(0.82f, 0.83f).y, n(0.68f, 0.92f).x, n(0.68f, 0.92f).y, n(0.5f, 0.92f).x, n(0.5f, 0.92f).y)
        cubicTo(n(0.32f, 0.92f).x, n(0.32f, 0.92f).y, n(0.18f, 0.83f).x, n(0.18f, 0.83f).y, n(0.18f, 0.66f).x, n(0.18f, 0.66f).y)
        cubicTo(n(0.18f, 0.52f).x, n(0.18f, 0.52f).y, n(0.5f, 0.10f).x, n(0.5f, 0.10f).y, n(0.5f, 0.10f).x, n(0.5f, 0.10f).y)
        close()
    }
    drawPath(drop, tint, style = stroke)
    val chevron = Path().apply {
        moveTo(n(0.36f, 0.72f).x, n(0.36f, 0.72f).y)
        lineTo(n(0.5f, 0.58f).x, n(0.5f, 0.58f).y)
        lineTo(n(0.64f, 0.72f).x, n(0.64f, 0.72f).y)
    }
    drawPath(chevron, tint, style = Stroke(width = strokeWidth * 0.75f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Vacuum/suction: three concentric partial arcs winding into a center dot, read as a vortex. */
private fun DrawScope.drawVacuumGlyph(tint: Color, strokeWidth: Float) {
    val center = n(0.5f, 0.55f)
    val radii = listOf(0.12f, 0.24f, 0.36f)
    radii.forEachIndexed { index, radiusFraction ->
        val radiusPx = radiusFraction * size.minDimension
        drawArc(
            color = tint,
            startAngle = -90f - index * 20f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(center.x - radiusPx, center.y - radiusPx),
            size = androidx.compose.ui.geometry.Size(radiusPx * 2f, radiusPx * 2f),
            style = roundStroke(strokeWidth * 0.85f)
        )
    }
    drawCircle(tint, radius = strokeWidth * 0.6f, center = center)
}

/** Push: a pusher bar on the left, two forward chevrons indicating push/forward motion. */
private fun DrawScope.drawPushGlyph(tint: Color, strokeWidth: Float) {
    drawLine(tint, n(0.20f, 0.24f), n(0.20f, 0.80f), strokeWidth * 1.2f, StrokeCap.Round)
    listOf(0f, 0.22f).forEach { xOffset ->
        val chevron = Path().apply {
            moveTo(n(0.40f + xOffset, 0.24f).x, n(0.40f + xOffset, 0.24f).y)
            lineTo(n(0.62f + xOffset, 0.52f).x, n(0.62f + xOffset, 0.52f).y)
            lineTo(n(0.40f + xOffset, 0.80f).x, n(0.40f + xOffset, 0.80f).y)
        }
        drawPath(chevron, tint, style = roundStroke(strokeWidth))
    }
}

private fun CleaningIntensity.displayName(): String = when (this) {
    CleaningIntensity.GENTLE -> "Gentle"
    CleaningIntensity.STANDARD -> "Standard"
    CleaningIntensity.POWERFUL -> "Powerful"
}

@Composable
private fun DriveModeCard(
    title: String,
    glyph: ModeGlyph,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) SakarColors.PrimarySoft else SakarColors.Surface,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) SakarColors.Primary else SakarColors.Border)
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                ModeIcon(
                    glyph = glyph,
                    tint = if (!enabled) SakarColors.TextSubtle.copy(alpha = 0.7f) else SakarColors.Primary,
                    size = 48.dp
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (!enabled) SakarColors.TextSubtle else SakarColors.TextPrimary,
                    textAlign = TextAlign.Center
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).size(22.dp).background(SakarColors.Primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun PowerButton(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) SakarColors.PrimarySoft else SakarColors.Background,
        border = if (selected) BorderStroke(2.dp, SakarColors.Primary) else null
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = SakarColors.TextPrimary
            )
        }
    }
}
