package com.sakarrobotics.installationassistant

import android.app.Application
import android.content.Context
import androidx.activity.result.ActivityResultRegistryOwner
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installationassistant.data.FileKeyValueStore
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application holder for the single [AssistantServices] instance. Every repository behind it is
 * SIMULATED/local; there is no robot, network or ROS code in this process.
 */
class AssistantApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val services: AssistantServices by lazy {
        AssistantServices(
            store = FileKeyValueStore(File(filesDir, "demo-data")),
            scope = appScope
        )
    }

    companion object {
        /** Test hook: instrumentation tests install an in-memory [AssistantServices] before launching. */
        @Volatile
        var servicesOverride: AssistantServices? = null

        /** Test hook: replaces the system share sheet so tests can observe the PDF that would be shared. */
        @Volatile
        var shareOverride: ((Context, File) -> Result<Unit>)? = null

        /** Test hook: replaces the system file pickers (Storage Access Framework) with scripted results. */
        @Volatile
        var activityResultRegistryOwnerOverride: ActivityResultRegistryOwner? = null

        fun servicesFor(context: Context): AssistantServices =
            servicesOverride ?: (context.applicationContext as AssistantApp).services

        /** Version shown on the About screen. */
        fun versionName(context: Context): String = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
