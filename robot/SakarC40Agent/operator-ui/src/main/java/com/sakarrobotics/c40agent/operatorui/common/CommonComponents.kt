package com.sakarrobotics.c40agent.operatorui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.domain.model.Capability
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/** Renders how real a value/action is - every screen that shows robot data must use this. */
@Composable
fun CapabilityBadge(capability: Capability, modifier: Modifier = Modifier) {
    val (label, bg, fg) = when (capability) {
        Capability.REAL -> Triple("LIVE", SakarColors.Success.copy(alpha = 0.12f), SakarColors.Success)
        Capability.LOCAL -> Triple("SAVED LOCALLY", SakarColors.Info.copy(alpha = 0.12f), SakarColors.Info)
        Capability.SIMULATED -> Triple("SIMULATED", SakarColors.Warning.copy(alpha = 0.14f), SakarColors.WarningText)
        Capability.GATED -> Triple("LOCKED (SERVICE MODE)", SakarColors.Warning.copy(alpha = 0.14f), SakarColors.WarningText)
        Capability.UNAVAILABLE -> Triple("NOT AVAILABLE", SakarColors.Danger.copy(alpha = 0.10f), SakarColors.Danger)
    }
    Surface(color = bg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun SakarTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Surface(color = SakarColors.Surface, shadowElevation = 1.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SakarColors.TextPrimary)
                }
            } else {
                Box(Modifier.size(48.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = SakarColors.TextPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )
            Box(Modifier.fillMaxWidth().weight(1f, fill = true)) {}
            trailing()
        }
    }
}

/**
 * Large, touch-friendly action tile for the home dashboard / category grids - robot-mounted
 * screens need big targets. [emphasized], [height], [contentPadding], [titleStyle] and
 * [horizontalAlignment] all default to the original look (plain surface, 132dp, 18dp padding,
 * titleMedium, start-aligned) so every existing call site is unaffected; a caller opts into a
 * bigger/centered look only by passing them explicitly. Passing [Dp.Unspecified] for [height]
 * skips the fixed-height modifier so a caller can size the card itself instead (e.g. via
 * `Modifier.weight(1f).fillMaxHeight()` in an even grid). [titleFirst] swaps the title/icon
 * order (title on top, icon at the bottom) for layouts that want that reading order instead of
 * the original icon-then-title. [icon] is nullable - when null, the title is simply centered in
 * the card (no icon slot at all), matching the vendor reference's icon-less tiles (Teaching Mode,
 * Manual Drive). [iconAlignment] defaults to [horizontalAlignment] but lets a caller keep a
 * centered title while left-aligning the icon underneath it, matching the vendor reference's
 * bottom-left icon placement (Start Cleaning, Scheduled Cleaning).
 */
@Composable
fun BigActionCard(
    title: String,
    subtitle: String? = null,
    icon: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    height: Dp = 132.dp,
    contentPadding: Dp = 18.dp,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    iconAlignment: Alignment.Horizontal = horizontalAlignment,
    titleFirst: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .then(if (height != Dp.Unspecified) Modifier.height(height) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (emphasized) SakarColors.PrimarySoft else SakarColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (emphasized) 4.dp else 1.dp),
        shape = RoundedCornerShape(12.dp),
        border = if (emphasized) BorderStroke(1.5.dp, SakarColors.Primary) else null
    ) {
        val titleBlock = @Composable {
            Column(horizontalAlignment = horizontalAlignment) {
                Text(
                    title,
                    style = titleStyle,
                    fontWeight = FontWeight.Bold,
                    textAlign = if (horizontalAlignment == Alignment.CenterHorizontally) TextAlign.Center else TextAlign.Start,
                    color = if (enabled) SakarColors.TextPrimary else SakarColors.TextSubtle
                )
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
                }
            }
        }
        if (icon == null) {
            Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                titleBlock()
            }
        } else {
            val iconArrangement = when (iconAlignment) {
                Alignment.End -> Arrangement.End
                Alignment.CenterHorizontally -> Arrangement.Center
                else -> Arrangement.Start
            }
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = horizontalAlignment
            ) {
                if (titleFirst) {
                    titleBlock()
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = iconArrangement) { icon() }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = iconArrangement) { icon() }
                    titleBlock()
                }
            }
        }
    }
}

@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SakarColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = SakarColors.TextPrimary)
                Box(Modifier.height(8.dp))
            }
            content()
        }
    }
}

@Composable
fun StatusPill(label: String, value: String, tint: Color = SakarColors.TextPrimary) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SakarColors.TextFaint)
        Text(value, style = MaterialTheme.typography.titleMedium, color = tint, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun InlineBanner(message: String, isWarning: Boolean = true, onClick: (() -> Unit)? = null) {
    Surface(
        color = if (isWarning) SakarColors.Warning.copy(alpha = 0.12f) else SakarColors.Info.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                if (isWarning) Icons.Filled.Warning else Icons.Filled.Info,
                contentDescription = null,
                tint = if (isWarning) SakarColors.WarningText else SakarColors.Info
            )
            Text(message, color = if (isWarning) SakarColors.WarningText else SakarColors.Info, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = SakarColors.TextSubtle, modifier = Modifier.size(40.dp))
        Text(message, color = SakarColors.TextSubtle, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun SuccessDot(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(10.dp).background(SakarColors.Success, CircleShape))
}

val ScreenPadding = PaddingValues(16.dp)

@Composable
fun CheckmarkIcon() {
    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = SakarColors.Success)
}
