package com.sakarrobotics.installationassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sakarrobotics.installation.core.AssistantServices
import com.sakarrobotics.installationassistant.ui.checklist.ChecklistScreen
import com.sakarrobotics.installationassistant.ui.diagnostics.DiagnosticsScreen
import com.sakarrobotics.installationassistant.ui.floors.FloorsScreen
import com.sakarrobotics.installationassistant.ui.home.HomeScreen
import com.sakarrobotics.installationassistant.ui.mapping.MappingScreen
import com.sakarrobotics.installationassistant.ui.report.ReportScreen
import com.sakarrobotics.installationassistant.ui.settings.SettingsScreen
import com.sakarrobotics.installationassistant.ui.wizard.WizardScreen

val LocalServices = staticCompositionLocalOf<AssistantServices> {
    error("AssistantServices not provided")
}

object Routes {
    const val HOME = "home"
    const val WIZARD = "wizard"
    const val MAPPING = "mapping?floorId={floorId}"
    const val FLOORS = "floors"
    const val DIAGNOSTICS = "diagnostics"
    const val CHECKLIST = "checklist"
    const val REPORT = "report"
    const val SETTINGS = "settings"

    fun mapping(floorId: String? = null) = if (floorId == null) "mapping" else "mapping?floorId=$floorId"
}

@Composable
fun AssistantNavHost(services: AssistantServices) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    CompositionLocalProvider(LocalServices provides services) {
        NavHost(navController = nav, startDestination = Routes.HOME) {
            composable(Routes.HOME) {
                HomeScreen(
                    onNewInstallation = { nav.navigate(Routes.WIZARD) },
                    onMapping = { nav.navigate(Routes.mapping()) },
                    onDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onFloors = { nav.navigate(Routes.FLOORS) },
                    onChecklist = { nav.navigate(Routes.CHECKLIST) },
                    onReport = { nav.navigate(Routes.REPORT) },
                    onSettings = { nav.navigate(Routes.SETTINGS) }
                )
            }
            composable(Routes.WIZARD) {
                WizardScreen(
                    onBack = back,
                    onOpenMapping = { floorId -> nav.navigate(Routes.mapping(floorId)) },
                    onOpenFloors = { nav.navigate(Routes.FLOORS) },
                    onOpenDiagnostics = { nav.navigate(Routes.DIAGNOSTICS) },
                    onOpenChecklist = { nav.navigate(Routes.CHECKLIST) },
                    onOpenReport = { nav.navigate(Routes.REPORT) },
                    onFinished = { nav.popBackStack(Routes.HOME, inclusive = false) }
                )
            }
            composable(
                Routes.MAPPING,
                arguments = listOf(navArgument("floorId") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { entry ->
                MappingScreen(
                    initialFloorId = entry.arguments?.getString("floorId"),
                    onBack = back,
                    onOpenFloors = { nav.navigate(Routes.FLOORS) }
                )
            }
            composable(Routes.FLOORS) {
                FloorsScreen(onBack = back, onOpenMapping = { floorId -> nav.navigate(Routes.mapping(floorId)) })
            }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = back) }
            composable(Routes.CHECKLIST) { ChecklistScreen(onBack = back) }
            composable(Routes.REPORT) { ReportScreen(onBack = back) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = back) }
        }
    }
}
