package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

val Context.dataStore by preferencesDataStore(name = "vpn_settings")

class TrialManager(private val context: Context) {
    private val FIRST_LAUNCH_KEY = longPreferencesKey("first_launch_time")
    private val TRIAL_DAYS = 7L
    private val TRIAL_MILLIS = TimeUnit.DAYS.toMillis(TRIAL_DAYS)

    suspend fun initializeFirstLaunch() {
        context.dataStore.edit { preferences ->
            if (!preferences.contains(FIRST_LAUNCH_KEY)) {
                preferences[FIRST_LAUNCH_KEY] = System.currentTimeMillis()
            }
        }
    }

    val remainingDays: Flow<Long> = context.dataStore.data.map { preferences ->
        val firstLaunch = preferences[FIRST_LAUNCH_KEY] ?: return@map TRIAL_DAYS
        val now = System.currentTimeMillis()
        val elapsed = now - firstLaunch
        val remaining = TRIAL_MILLIS - elapsed
        if (remaining <= 0) 0L else TimeUnit.MILLISECONDS.toDays(remaining) + 1
    }

    val isTrialExpired: Flow<Boolean> = context.dataStore.data.map { preferences ->
        val firstLaunch = preferences[FIRST_LAUNCH_KEY] ?: return@map false
        val now = System.currentTimeMillis()
        val elapsed = now - firstLaunch
        elapsed > TRIAL_MILLIS
    }
}
