package com.sakarrobotics.c40agent.operatorui.superuser

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.usecase.AuthUseCases
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.common.SakarTopBar
import com.sakarrobotics.c40agent.operatorui.theme.SakarColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SuperUserAuthViewModel(container: AppContainer) : ViewModel() {
    private val useCases = AuthUseCases(container.authRepository)

    var isConfigured by androidx.compose.runtime.mutableStateOf(true)
        private set
    var errorMessage by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch { isConfigured = useCases.isSuperUserConfigured() }
    }

    fun setPin(pin: String, confirm: String, onSuccess: () -> Unit) {
        if (pin != confirm) {
            errorMessage = "PINs do not match"
            return
        }
        viewModelScope.launch {
            useCases.setPin(pin).onSuccess {
                isConfigured = true
                onSuccess()
            }.onFailure { errorMessage = it.message }
        }
    }

    fun tryLogin(pin: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            if (useCases.tryEnterSuperUser(pin)) {
                errorMessage = null
                onSuccess()
            } else {
                errorMessage = "Incorrect PIN"
            }
        }
    }
}

/**
 * Same circular-keypad visual as the idle-lock screensaver's PIN pad (IdleLock.kt), but - unlike
 * that pad, which is a UI-only placeholder where any 4 digits unlock it - every digit entered
 * here goes through the real SuperUserAuthViewModel: SHA-256(salt+pin) verified against
 * DataStoreAuthRepository, or written there on first-run setup. No bypass, no shared code with
 * the idle-lock pad's fake unlock.
 *
 * First-run (no PIN configured yet) is two stages - enter a new PIN, then confirm it - each still
 * a plain 4-digit pad; a mismatch resets back to the first stage rather than leaving a stale
 * "confirm" step around.
 */
@Composable
fun SuperUserLoginScreen(onBack: () -> Unit, onAuthenticated: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: SuperUserAuthViewModel = viewModel(factory = viewModelFactory { initializer { SuperUserAuthViewModel(container) } })

    var pin by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            delay(150)
            val entered = pin
            pin = ""
            if (!viewModel.isConfigured) {
                val first = firstPin
                if (first == null) {
                    firstPin = entered
                } else {
                    firstPin = null
                    viewModel.setPin(first, entered, onAuthenticated)
                }
            } else {
                viewModel.tryLogin(entered, onAuthenticated)
            }
        }
    }

    val title = when {
        !viewModel.isConfigured && firstPin == null -> "Set a Super User PIN"
        !viewModel.isConfigured -> "Confirm Super User PIN"
        else -> "Enter Super User PIN"
    }

    Scaffold(topBar = { SakarTopBar("Super User Access", onBack) }, containerColor = SakarColors.Background) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = SakarColors.TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(if (index < pin.length) SakarColors.Primary else SakarColors.Border, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(36.dp))
                val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"))
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { digit -> SuperUserPinKey(label = digit, onClick = { if (pin.length < 4) pin += digit }) }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    SuperUserPinKey(icon = Icons.Filled.Refresh, contentDescription = "Clear", onClick = { pin = "" })
                    SuperUserPinKey(label = "0", onClick = { if (pin.length < 4) pin += "0" })
                    SuperUserPinKey(icon = Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", onClick = { pin = pin.dropLast(1) })
                }
                if (viewModel.errorMessage != null) {
                    Spacer(Modifier.height(24.dp))
                    Text(viewModel.errorMessage ?: "", color = SakarColors.Danger)
                }
            }
        }
    }
}

@Composable
private fun SuperUserPinKey(
    label: String? = null,
    icon: ImageVector? = null,
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
