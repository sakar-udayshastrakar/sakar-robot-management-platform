package com.sakarrobotics.c40agent.operatorui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.sakarrobotics.c40agent.operatorui.common.EmptyState
import com.sakarrobotics.c40agent.operatorui.common.InlineBanner
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.common.ScreenPadding
import com.sakarrobotics.c40agent.operatorui.common.SectionCard
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors

/**
 * Real Wi-Fi status via the Android tablet's own [WifiManager] (this is
 * the on-robot tablet's network stack - the Peanut SDK exposes no
 * separate Wi-Fi API). Nearby-network scanning requires
 * ACCESS_FINE_LOCATION at runtime (Android platform requirement since
 * API 23) - the screen requests it explicitly rather than silently
 * failing.
 */
@Composable
fun NetworkScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasLocationPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocationPermission = granted
    }

    val wifiManager = remember { context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager }
    val isWifiEnabled = remember { wifiManager.isWifiEnabled }
    val connectionInfo = remember(hasLocationPermission) { if (hasLocationPermission) wifiManager.connectionInfo else null }

    Scaffold(topBar = { SakarTopBar("Wi-Fi / Network", onBack) }, containerColor = SakarColors.Background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(ScreenPadding)) {
            SectionCard(title = "Current Connection") {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = if (isWifiEnabled) SakarColors.Success else SakarColors.TextSubtle)
                    Text(if (isWifiEnabled) "Wi-Fi enabled" else "Wi-Fi disabled", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(8.dp))
                if (!hasLocationPermission) {
                    InlineBanner("Location permission is required by Android to read the connected network name and scan for nearby networks.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                        Text("Grant Permission")
                    }
                } else {
                    val ssid = connectionInfo?.ssid?.trim('"')
                    Text("SSID: ${ssid ?: "Unknown"}", color = SakarColors.TextMuted)
                    Text("Link speed: ${connectionInfo?.linkSpeed ?: "-"} Mbps", color = SakarColors.TextFaint)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Available Networks", style = MaterialTheme.typography.titleMedium, color = SakarColors.TextPrimary)
            Spacer(Modifier.height(8.dp))
            if (!hasLocationPermission) {
                EmptyState("Grant location permission to scan for nearby networks")
            } else {
                val scanResults = remember { runCatching { wifiManager.scanResults }.getOrDefault(emptyList()) }
                if (scanResults.isEmpty()) {
                    EmptyState("No scan results yet")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(scanResults.distinctBy { it.SSID }.take(20)) { result ->
                            SectionCard {
                                Text(result.SSID.ifBlank { "(hidden network)" }, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }
        }
    }
}
