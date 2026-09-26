package com.sakarrobotics.c40agent.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sakarrobotics.c40agent.domain.model.BusinessProfile
import com.sakarrobotics.c40agent.domain.model.LocalPreferences
import com.sakarrobotics.c40agent.domain.model.ScreenFace
import com.sakarrobotics.c40agent.domain.repository.LocalPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.localPrefsDataStore by preferencesDataStore(name = "sakar_local_preferences")

class DataStoreLocalPreferencesRepository(private val appContext: Context) : LocalPreferencesRepository {

    private object Keys {
        val language = stringPreferencesKey("language")
        val musicVolume = intPreferencesKey("music_volume")
        val voiceVolume = intPreferencesKey("voice_volume")
        val screenLockDurationSeconds = intPreferencesKey("screen_lock_duration_seconds")
        val screenLockPinConfigured = booleanPreferencesKey("screen_lock_pin_configured")
        val screenFace = stringPreferencesKey("screen_face")
        val promotionalContentEnabled = booleanPreferencesKey("promotional_content_enabled")
        val musicDndEnabled = booleanPreferencesKey("music_dnd_enabled")
        val taskDndEnabled = booleanPreferencesKey("task_dnd_enabled")
        val resumeFromBreakpoint = booleanPreferencesKey("resume_from_breakpoint")
        val sweepingBrushAutoHeight = booleanPreferencesKey("sweeping_brush_auto_height")
        val businessProfile = stringPreferencesKey("business_profile")
    }

    override val preferences: Flow<LocalPreferences> = appContext.localPrefsDataStore.data.map { prefs ->
        LocalPreferences(
            language = prefs[Keys.language] ?: "English",
            musicVolume = prefs[Keys.musicVolume] ?: 50,
            voiceVolume = prefs[Keys.voiceVolume] ?: 80,
            screenLockDurationSeconds = prefs[Keys.screenLockDurationSeconds] ?: 15,
            screenLockPinConfigured = prefs[Keys.screenLockPinConfigured] ?: false,
            screenFace = prefs[Keys.screenFace]?.let { ScreenFace.valueOf(it) } ?: ScreenFace.BLUE_EYES,
            promotionalContentEnabled = prefs[Keys.promotionalContentEnabled] ?: false,
            musicDndEnabled = prefs[Keys.musicDndEnabled] ?: false,
            taskDndEnabled = prefs[Keys.taskDndEnabled] ?: false,
            resumeFromBreakpoint = prefs[Keys.resumeFromBreakpoint] ?: false,
            sweepingBrushAutoHeight = prefs[Keys.sweepingBrushAutoHeight] ?: true,
            businessProfile = prefs[Keys.businessProfile]?.let { BusinessProfile.valueOf(it) } ?: BusinessProfile.SR_CLEANING
        )
    }

    override suspend fun update(transform: (LocalPreferences) -> LocalPreferences) {
        val current = preferences.first()
        val next = transform(current)
        appContext.localPrefsDataStore.edit { prefs ->
            prefs[Keys.language] = next.language
            prefs[Keys.musicVolume] = next.musicVolume
            prefs[Keys.voiceVolume] = next.voiceVolume
            prefs[Keys.screenLockDurationSeconds] = next.screenLockDurationSeconds
            prefs[Keys.screenLockPinConfigured] = next.screenLockPinConfigured
            prefs[Keys.screenFace] = next.screenFace.name
            prefs[Keys.promotionalContentEnabled] = next.promotionalContentEnabled
            prefs[Keys.musicDndEnabled] = next.musicDndEnabled
            prefs[Keys.taskDndEnabled] = next.taskDndEnabled
            prefs[Keys.resumeFromBreakpoint] = next.resumeFromBreakpoint
            prefs[Keys.sweepingBrushAutoHeight] = next.sweepingBrushAutoHeight
            prefs[Keys.businessProfile] = next.businessProfile.name
        }
    }
}
