package com.sakarrobotics.installationassistant.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sakarrobotics.installationassistant.ui.LocalServices
import com.sakarrobotics.installationassistant.ui.theme.SakarColors

const val MIN_TOUCH = 48

enum class Tone(val fg: Color, val bg: Color) {
    NEUTRAL(SakarColors.Muted, Color(0xFFEFF2F6)),
    SUCCESS(SakarColors.Success, SakarColors.SuccessSoft),
    WARNING(SakarColors.Warning, SakarColors.WarningSoft),
    DANGER(SakarColors.Danger, SakarColors.DangerSoft),
    INFO(SakarColors.Info, SakarColors.InfoSoft),
    BRAND(SakarColors.Warning, SakarColors.OrangeSoftStrong)
}

/** Text wordmark: original Sakar branding, no image assets. */
@Composable
fun SakarWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier.semantics { contentDescription = "Sakar Robotics" }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).background(SakarColors.Orange, RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(14.dp).background(Color.White, RoundedCornerShape(50)))
        }
        Spacer(Modifier.size(10.dp))
        Text("Sakar", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SakarColors.Orange)
        Text(" Robotics", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = SakarColors.Ink)
    }
}

/** Always-visible notice that nothing on screen comes from a robot. Shown on every screen. */
@Composable
fun SimulatedBanner(modifier: Modifier = Modifier) {
    Surface(
        color = SakarColors.OrangeSoftStrong,
        modifier = modifier.fillMaxWidth().testTag("simulated_banner")
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = SakarColors.Warning, modifier = Modifier.size(20.dp))
            Text(
                "SIMULATED BACKEND - no robot is connected. Everything shown is demo data.",
                style = MaterialTheme.typography.labelLarge,
                color = SakarColors.Warning,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SimulatedTag(modifier: Modifier = Modifier) = StatusChip("SIMULATED", Tone.BRAND, modifier = modifier)

@Composable
fun StatusChip(text: String, tone: Tone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(color = tone.bg, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = tone.fg, modifier = Modifier.size(16.dp))
            Text(text, color = tone.fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AssistantScaffold(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier.fillMaxSize().background(SakarColors.Background)) {
        Surface(color = SakarColors.Surface, shadowElevation = 2.dp) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.size(MIN_TOUCH.dp).testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SakarColors.Ink)
                    }
                } else {
                    Spacer(Modifier.size(16.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = SakarColors.Ink,
                    modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { contentDescription = "Screen: $title" }
                )
                trailing()
            }
        }
        SimulatedBanner()
        val persistence = LocalServices.current.persistence
        val storageError by persistence.error.collectAsState()
        storageError?.let {
            MessageBanner(
                it, Tone.DANGER,
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag("persistence_error"),
                onDismiss = persistence::dismiss
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SakarColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, SakarColors.Border)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    trailing()
                }
            }
            content()
        }
    }
}

@Composable
fun MessageBanner(
    text: String,
    tone: Tone,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    val icon = when (tone) {
        Tone.SUCCESS -> Icons.Filled.CheckCircle
        Tone.DANGER -> Icons.Filled.Error
        Tone.WARNING -> Icons.Filled.Warning
        else -> Icons.Filled.Info
    }
    Surface(color = tone.bg, shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = if (onDismiss == null) 14.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tone.fg)
            Text(text, color = tone.fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(MIN_TOUCH.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss message", tint = tone.fg)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = SakarColors.Faint, modifier = Modifier.size(40.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(4.dp))
            AssistantButton(actionLabel, onAction, modifier = Modifier.testTag("empty_state_action"))
        }
    }
}

@Composable
fun LoadingBlock(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(16.dp).semantics { contentDescription = "Loading: $text" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = SakarColors.Orange)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
    }
}

enum class ButtonKind { PRIMARY, SECONDARY, DANGER }

@Composable
fun AssistantButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.PRIMARY,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val m = modifier.defaultMinSize(minHeight = MIN_TOUCH.dp)
    val content: @Composable () -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
    when (kind) {
        ButtonKind.PRIMARY -> Button(onClick = onClick, modifier = m, enabled = enabled, content = { content() })
        ButtonKind.DANGER -> Button(
            onClick = onClick, modifier = m, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = SakarColors.Danger),
            content = { content() }
        )
        ButtonKind.SECONDARY -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled,
            border = BorderStroke(1.dp, if (enabled) SakarColors.Orange else SakarColors.Border),
            content = { content() }
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            AssistantButton(
                confirmLabel, onConfirm,
                kind = if (danger) ButtonKind.DANGER else ButtonKind.PRIMARY,
                modifier = Modifier.testTag("confirm_button")
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = MIN_TOUCH.dp).testTag("cancel_button")) {
                Text("Cancel")
            }
        }
    )
}

/** Name entry dialog. [onConfirm] returns an error message to keep the dialog open, or null to close it. */
@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onConfirm: (String) -> String?,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text(label) },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth().testTag("name_input")
                )
                if (error != null) {
                    Text(error!!, color = SakarColors.Danger, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("name_error"))
                }
            }
        },
        confirmButton = {
            AssistantButton(confirmLabel, onClick = {
                val result = onConfirm(value)
                if (result == null) onDismiss() else error = result
            }, modifier = Modifier.testTag("confirm_button"))
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.defaultMinSize(minHeight = MIN_TOUCH.dp).testTag("cancel_button")) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BigTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false
) {
    Card(
        modifier = modifier
            .heightIn(min = 132.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "$title. $subtitle" },
        colors = CardDefaults.cardColors(containerColor = if (emphasized) SakarColors.OrangeSoft else SakarColors.Surface),
        border = BorderStroke(if (emphasized) 1.5.dp else 1.dp, if (emphasized) SakarColors.Orange else SakarColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, contentDescription = null, tint = SakarColors.Orange, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SakarColors.Muted)
        }
    }
}
