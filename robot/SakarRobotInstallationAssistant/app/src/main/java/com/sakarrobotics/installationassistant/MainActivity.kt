package com.sakarrobotics.installationassistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import com.sakarrobotics.installationassistant.ui.AssistantNavHost
import com.sakarrobotics.installationassistant.ui.theme.SakarTheme

class MainActivity : ComponentActivity() {

    private lateinit var services: com.sakarrobotics.installation.core.AssistantServices

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        services = AssistantApp.servicesFor(this)
        val registryOverride = AssistantApp.activityResultRegistryOwnerOverride
        setContent {
            val content: @androidx.compose.runtime.Composable () -> Unit = {
                SakarTheme {
                    AssistantNavHost(services)
                }
            }
            if (registryOverride != null) {
                CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOverride, content = content)
            } else {
                content()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // A simulated mapping session must not keep ticking while the app is out of sight.
        services.mapping.interrupt("Paused because the app was in the background.")
    }
}
