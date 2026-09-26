package com.sakarrobotics.c40agent.operatorui.nav

/** Central route table for the Sakar CleanBot operator app's single NavHost. */
object Routes {
    const val HOME = "home"

    const val START_CLEANING = "start_cleaning"
    const val SCHEDULE_LIST = "schedule_list"
    const val SCHEDULE_EDIT = "schedule_edit/{taskId}"
    fun scheduleEdit(taskId: String) = "schedule_edit/$taskId"
    const val SCHEDULE_EDIT_NEW = "schedule_edit/new"

    const val MANUAL_DRIVE = "manual_drive"

    const val TEACH_ROUTE_LIST = "teach_route_list"
    const val TEACH_ROUTE_RECORD = "teach_route_record"

    const val SETTINGS_ROOT = "settings_root"
    const val SETTINGS_CONSUMABLES = "settings_consumables"
    const val SETTINGS_CLEANING_DATA = "settings_cleaning_data"
    const val SETTINGS_WORKSTATION = "settings_workstation"
    const val SETTINGS_CHARGING = "settings_charging"
    const val SETTINGS_NETWORK = "settings_network"
    const val SETTINGS_DISPLAY = "settings_display"
    const val SETTINGS_SOUND = "settings_sound"
    const val SETTINGS_SCREEN_LOCK = "settings_screen_lock"
    const val SETTINGS_GENERAL = "settings_general"
    const val SETTINGS_ROBOT_INFO = "settings_robot_info"
    const val SETTINGS_RESOURCE_MGMT = "settings_resource_mgmt"
    const val SETTINGS_SYSTEM_INFO = "settings_system_info"

    const val SUPER_USER_LOGIN = "super_user_login"
    const val SUPER_USER_HOME = "super_user_home"
    const val SUPER_USER_DEBUG_GROUPS = "super_user_debug_groups"
    const val SUPER_USER_DEBUG_GROUP = "super_user_debug_group/{groupId}"
    fun debugGroup(groupId: String) = "super_user_debug_group/$groupId"
    const val SUPER_USER_LOGS = "super_user_logs"
    const val SUPER_USER_SYSTEM_SETTINGS = "super_user_system_settings"

    const val INSTALLATION_HOME = "installation_home"
    const val INSTALLATION_ELEVATOR = "installation_elevator"
    const val INSTALLATION_SCHEDULING_PATH = "installation_scheduling_path"
    const val INSTALLATION_REMOTE_CONTROL = "installation_remote_control"
    const val INSTALLATION_ADVANCED = "installation_advanced"

    const val MAPS_LIST = "maps_list"
    const val MAP_DETAIL = "map_detail/{mapId}"
    fun mapDetail(mapId: String) = "map_detail/$mapId"
}
