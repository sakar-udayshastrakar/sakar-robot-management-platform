package com.sakarrobotics.c40agent.operatorui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

private const val IDLE_TIMEOUT_MS = 30_000L

private enum class LockState { UNLOCKED, SCREENSAVER, PIN_ENTRY }

/**
 * Wraps [content] with an idle-triggered lock, matching the reference: after
 * [IDLE_TIMEOUT_MS] of no touch input the screen shows a full-screen "eyes" screensaver;
 * tapping it opens a PIN pad; the dashboard reappears once 4 digits are entered.
 *
 * There is no operator-PIN backend anywhere in this project, so the PIN pad is a UI-only
 * placeholder for this checkpoint: any 4 digits unlock, exactly like this app's other
 * not-yet-implemented controls (Manual Push, Recharge, Mute, Light). This is not real
 * access control - it only reproduces the reference's interaction pattern.
 */
@Composable
fun IdleLockController(screensaverCaption: String? = null, content: @Composable () -> Unit) {
    var lockState by remember { mutableStateOf(LockState.UNLOCKED) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(lockState) {
        if (lockState == LockState.UNLOCKED) {
            while (true) {
                delay(1_000)
                if (System.currentTimeMillis() - lastInteractionAt >= IDLE_TIMEOUT_MS) {
                    lockState = LockState.SCREENSAVER
                    break
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                detectTapGestures(onPress = { lastInteractionAt = System.currentTimeMillis() })
            }
        ) {
            content()
        }
        when (lockState) {
            LockState.SCREENSAVER -> EyesScreensaver(caption = screensaverCaption, onWake = { lockState = LockState.PIN_ENTRY })
            LockState.PIN_ENTRY -> PinEntryScreen(
                onUnlock = {
                    lastInteractionAt = System.currentTimeMillis()
                    lockState = LockState.UNLOCKED
                }
            )
            LockState.UNLOCKED -> Unit
        }
    }
}

/**
 * Idle face: two eyes that blink (on a randomized schedule, occasionally twice in a row - an
 * even fixed rhythm reads mechanical) and drift/breathe subtly so the panel reads as alive, not
 * frozen. The eyes keep the reference recording's cyan-to-teal gradient and horizontal-breathing
 * pulse (verified earlier by extracting and comparing real frames from that clip); blinking and
 * look-around drift are additions this project's own reference clip was too short to confirm or
 * rule out, so they're kept deliberately subtle. Any touch (not just a tap - drags too) hands the
 * screen back, consumed here so it never reaches the dashboard underneath.
 */
@Composable
private fun EyesScreensaver(caption: String?, onWake: () -> Unit) {
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2400L, 5600L))
            blink.animateTo(0.05f, tween(90, easing = FastOutLinearInEasing))
            blink.animateTo(1f, tween(150, easing = LinearOutSlowInEasing))
            if (Random.nextInt(5) == 0) {
                delay(120)
                blink.animateTo(0.05f, tween(80, easing = FastOutLinearInEasing))
                blink.animateTo(1f, tween(130, easing = LinearOutSlowInEasing))
            }
        }
    }

    val drift = rememberInfiniteTransition(label = "faceDrift")
    val lookX by drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lookX"
    )
    val lookY by drift.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "lookY"
    )
    val breathe by drift.animateFloat(
        initialValue = 0.94f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )

    // Colors and "shine" pixel-sampled directly from a reference screenshot at several depths
    // through the eye (2%/8%/15%/25%/40%/50%/65%/80%/92%/98%) rather than eyeballed: the top
    // ~15% holds a bright, near-constant cyan plateau (the shine) before smoothly darkening to a
    // deep navy-blue at the bottom - a multi-stop gradient, not a plain linear one.
    val eyeShine = Color(0xFF46FDF9)
    val eyeUpperMid = Color(0xFF14EBEF)
    val eyeMid = Color(0xFF12BCD5)
    val eyeLowerMid = Color(0xFF0B84B5)
    val eyeBottom = Color(0xFF0961A4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        onWake()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            // Square-ish (width ~= height) and larger than before, with a smaller corner-radius
            // fraction so they read as rounded squares rather than full pill/stadium capsules.
            val eyeWidth = unit * 0.24f * breathe
            val eyeHeight = unit * 0.24f
            val gap = unit * 0.34f
            val centerX = size.width / 2f + lookX * unit * 0.04f
            val centerY = size.height / 2f + lookY * unit * 0.025f
            val glow = unit * 0.012f
            val cornerFraction = 0.22f
            val gradient = Brush.verticalGradient(
                0.00f to eyeShine,
                0.15f to eyeShine,
                0.25f to eyeUpperMid,
                0.50f to eyeMid,
                0.80f to eyeLowerMid,
                1.00f to eyeBottom
            )

            fun eye(left: Float) {
                val h = (eyeHeight * blink.value).coerceAtLeast(unit * 0.012f)
                drawRoundRect(
                    color = eyeShine.copy(alpha = 0.18f),
                    topLeft = Offset(left - glow, centerY - h / 2f - glow),
                    size = Size(eyeWidth + glow * 2f, h + glow * 2f),
                    cornerRadius = CornerRadius(min(eyeWidth, h + glow * 2f) * cornerFraction)
                )
                drawRoundRect(
                    brush = gradient,
                    topLeft = Offset(left, centerY - h / 2f),
                    size = Size(eyeWidth, h),
                    cornerRadius = CornerRadius(min(eyeWidth, h) * cornerFraction)
                )
            }

            eye(centerX - gap / 2f - eyeWidth)
            eye(centerX + gap / 2f)
        }

        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.titleMedium,
                color = eyeShine.copy(alpha = 0.55f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
private fun PinEntryScreen(onUnlock: () -> Unit) {
    var pin by remember { mutableStateOf("") }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(250)
            pin = ""
            onUnlock()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(SakarColors.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Please enter PIN", style = MaterialTheme.typography.headlineSmall, color = SakarColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(
                                if (index < pin.length) SakarColors.Primary else SakarColors.Border,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
            val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { digit -> PinKey(label = digit, onClick = { if (pin.length < 4) pin += digit }) }
                }
                Spacer(Modifier.height(20.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                PinKey(icon = Icons.Filled.Refresh, contentDescription = "Clear", onClick = { pin = "" })
                PinKey(label = "0", onClick = { if (pin.length < 4) pin += "0" })
                PinKey(icon = Icons.Filled.Backspace, contentDescription = "Backspace", onClick = { pin = pin.dropLast(1) })
            }
        }
    }
}

@Composable
private fun PinKey(
    label: String? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDescription: String? = null,
    onClick: () -> Unit
) {
    Surface(color = SakarColors.Surface, shape = CircleShape, modifier = Modifier.size(72.dp), onClick = onClick, shadowElevation = 1.dp) {
        Box(contentAlignment = Alignment.Center) {
            if (label != null) {
                Text(label, style = MaterialTheme.typography.headlineSmall, color = SakarColors.TextPrimary)
            } else if (icon != null) {
                Icon(icon, contentDescription = contentDescription, tint = SakarColors.TextMuted)
            }
        }
    }
}
