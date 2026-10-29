package com.sakarrobotics.c40agent.operatorui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Shared chrome for every Settings detail screen so the exact same content can be shown two ways:
 *  - `embedded = false` (default): a standalone screen with the app's back top-bar, for deep links.
 *  - `embedded = true`: inside the two-pane [SettingsRootScreen] right pane — a plain section title
 *    (no back arrow), matching the vendor reference's master-detail Settings layout.
 *
 * The [content] receives a [PaddingValues]; standalone mode passes the Scaffold insets, embedded
 * mode passes zero (the pane host already provides spacing), so each screen's existing
 * `.padding(padding)` call works unchanged in both modes.
 */
@Composable
fun SettingsPane(
    title: String,
    onBack: () -> Unit,
    embedded: Boolean,
    content: @Composable (PaddingValues) -> Unit
) {
    if (embedded) {
        Column(Modifier.fillMaxSize()) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = SakarColors.TextPrimary,
                modifier = Modifier.padding(start = 28.dp, top = 20.dp, bottom = 12.dp)
            )
            Box(Modifier.weight(1f).fillMaxWidth()) { content(PaddingValues(0.dp)) }
        }
    } else {
        Scaffold(topBar = { SakarTopBar(title, onBack) }, containerColor = SakarColors.Background) { padding ->
            content(padding)
        }
    }
}
