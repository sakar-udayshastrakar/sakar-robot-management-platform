package com.sakarrobotics.c40agent.operatorui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sakarrobotics.c40agent.domain.di.AppContainer
import com.sakarrobotics.c40agent.domain.model.ScheduleTask
import com.sakarrobotics.c40agent.operatorui.cleaning.StartCleaningScreen
import com.sakarrobotics.c40agent.operatorui.common.LocalAppContainer
import com.sakarrobotics.c40agent.operatorui.home.HomeScreen
import com.sakarrobotics.c40agent.operatorui.installation.InstallationAdvancedScreen
import com.sakarrobotics.c40agent.operatorui.installation.InstallationElevatorScreen
import com.sakarrobotics.c40agent.operatorui.installation.InstallationHomeScreen
import com.sakarrobotics.c40agent.operatorui.installation.InstallationRemoteControlScreen
import com.sakarrobotics.c40agent.operatorui.installation.InstallationSchedulingPathScreen
import com.sakarrobotics.c40agent.operatorui.manualdrive.ManualDriveScreen
import com.sakarrobotics.c40agent.operatorui.maps.MapDetailScreen
import com.sakarrobotics.c40agent.operatorui.maps.MapsListScreen
import com.sakarrobotics.c40agent.operatorui.schedule.ScheduleEditScreen
import com.sakarrobotics.c40agent.operatorui.schedule.ScheduleListScreen
import com.sakarrobotics.c40agent.operatorui.settings.ChargingSettingsScreen
import com.sakarrobotics.c40agent.operatorui.settings.CleaningDataScreen
import com.sakarrobotics.c40agent.operatorui.settings.ConsumablesScreen
import com.sakarrobotics.c40agent.operatorui.settings.DisplaySettingsScreen
import com.sakarrobotics.c40agent.operatorui.settings.GeneralSettingsScreen
import com.sakarrobotics.c40agent.operatorui.settings.NetworkScreen
import com.sakarrobotics.c40agent.operatorui.settings.ResourceManagementScreen
import com.sakarrobotics.c40agent.operatorui.settings.RobotInfoScreen
import com.sakarrobotics.c40agent.operatorui.settings.ScreenLockSettingsScreen
import com.sakarrobotics.c40agent.operatorui.settings.SettingsRootScreen
import com.sakarrobotics.c40agent.operatorui.settings.SoundSettingsScreen
import com.sakarrobotics.c40agent.operatorui.settings.SystemInfoScreen
import com.sakarrobotics.c40agent.operatorui.settings.WorkstationScreen
import com.sakarrobotics.c40agent.operatorui.superuser.LogsScreen
import com.sakarrobotics.c40agent.operatorui.superuser.RobotDebuggingGroupDetailScreen
import com.sakarrobotics.c40agent.operatorui.superuser.RobotHardwareDebugScreen
import com.sakarrobotics.c40agent.operatorui.superuser.SuperUserHomeScreen
import com.sakarrobotics.c40agent.operatorui.superuser.SuperUserLoginScreen
import com.sakarrobotics.c40agent.operatorui.superuser.SuperUserSystemSettingsScreen
import com.sakarrobotics.c40agent.operatorui.teachroute.TeachRouteScreen
import com.sakarrobotics.c40agent.operatorui.theme.SakarTheme
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun SakarOperatorApp(container: AppContainer) {
    CompositionLocalProvider(LocalAppContainer provides container) {
        SakarTheme {
            val navController = rememberNavController()
            SakarNavGraph(navController)
        }
    }
}

@Composable
private fun SakarNavGraph(navController: NavHostController) {
    val container = LocalAppContainer.current

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenStartCleaning = { navController.navigate(Routes.START_CLEANING) },
                onOpenSchedule = { navController.navigate(Routes.SCHEDULE_LIST) },
                onOpenManualDrive = { navController.navigate(Routes.MANUAL_DRIVE) },
                onOpenTeachRoute = { navController.navigate(Routes.TEACH_ROUTE_LIST) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_ROOT) },
                onOpenSuperUser = { navController.navigate(Routes.SUPER_USER_LOGIN) }
            )
        }
        composable(Routes.START_CLEANING) { StartCleaningScreen(onBack = navController::popBackStack) }
        composable(Routes.MANUAL_DRIVE) { ManualDriveScreen(onBack = navController::popBackStack) }
        composable(Routes.TEACH_ROUTE_LIST) { TeachRouteScreen(onBack = navController::popBackStack) }

        composable(Routes.SCHEDULE_LIST) {
            ScheduleListScreen(
                onBack = navController::popBackStack,
                onNew = { navController.navigate(Routes.SCHEDULE_EDIT_NEW) },
                onEdit = { id -> navController.navigate(Routes.scheduleEdit(id)) }
            )
        }
        composable(Routes.SCHEDULE_EDIT_NEW) { ScheduleEditScreen(existing = null, onBack = navController::popBackStack) }
        composable(
            Routes.SCHEDULE_EDIT,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType })
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")
            val schedules by container.scheduleRepository.schedules.collectAsState(initial = emptyList<ScheduleTask>())
            val existing = schedules.firstOrNull { it.id == taskId }
            ScheduleEditScreen(existing = existing, onBack = navController::popBackStack)
        }

        // Settings
        composable(Routes.SETTINGS_ROOT) {
            SettingsRootScreen(onBack = navController::popBackStack, onOpen = { navController.navigate(it) })
        }
        composable(Routes.SETTINGS_CONSUMABLES) { ConsumablesScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_CLEANING_DATA) { CleaningDataScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_WORKSTATION) { WorkstationScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_CHARGING) { ChargingSettingsScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_NETWORK) { NetworkScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_DISPLAY) { DisplaySettingsScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_SOUND) { SoundSettingsScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_SCREEN_LOCK) { ScreenLockSettingsScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_GENERAL) { GeneralSettingsScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_ROBOT_INFO) { RobotInfoScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_RESOURCE_MGMT) { ResourceManagementScreen(onBack = navController::popBackStack) }
        composable(Routes.SETTINGS_SYSTEM_INFO) { SystemInfoScreen(onBack = navController::popBackStack) }

        // Super User
        composable(Routes.SUPER_USER_LOGIN) {
            SuperUserLoginScreen(onBack = navController::popBackStack, onAuthenticated = {
                navController.navigate(Routes.SUPER_USER_HOME) { popUpTo(Routes.SUPER_USER_LOGIN) { inclusive = true } }
            })
        }
        composable(Routes.SUPER_USER_HOME) {
            SuperUserHomeScreen(
                onBack = navController::popBackStack,
                onOpen = { navController.navigate(it) },
                onExitSuperUser = {
                    container.authRepository.exitSuperUserMode()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            )
        }
        composable(Routes.SUPER_USER_DEBUG_GROUPS) {
            RobotHardwareDebugScreen(onBack = navController::popBackStack)
        }
        composable(Routes.SUPER_USER_DEBUG_GROUP, arguments = listOf(navArgument("groupId") { type = NavType.StringType })) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            RobotDebuggingGroupDetailScreen(groupId = groupId, onBack = navController::popBackStack)
        }
        composable(Routes.SUPER_USER_LOGS) { LogsScreen(onBack = navController::popBackStack) }
        composable(Routes.SUPER_USER_SYSTEM_SETTINGS) { SuperUserSystemSettingsScreen(onBack = navController::popBackStack) }

        // Installation
        composable(Routes.INSTALLATION_HOME) {
            InstallationHomeScreen(onBack = navController::popBackStack, onOpen = { navController.navigate(it) })
        }
        composable(Routes.INSTALLATION_ELEVATOR) { InstallationElevatorScreen(onBack = navController::popBackStack) }
        composable(Routes.INSTALLATION_SCHEDULING_PATH) { InstallationSchedulingPathScreen(onBack = navController::popBackStack) }
        composable(Routes.INSTALLATION_REMOTE_CONTROL) { InstallationRemoteControlScreen(onBack = navController::popBackStack) }
        composable(Routes.INSTALLATION_ADVANCED) { InstallationAdvancedScreen(onBack = navController::popBackStack) }

        // Maps
        composable(Routes.MAPS_LIST) { MapsListScreen(onBack = navController::popBackStack, onOpenMap = { navController.navigate(Routes.mapDetail(it)) }) }
        composable(Routes.MAP_DETAIL, arguments = listOf(navArgument("mapId") { type = NavType.StringType })) { backStackEntry ->
            val mapId = backStackEntry.arguments?.getString("mapId") ?: ""
            MapDetailScreen(mapId = mapId, onBack = navController::popBackStack)
        }
    }
}
