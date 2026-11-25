package com.pricelens.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pricelens.domain.model.UnitPrice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    val lastUnit: Flow<UnitPrice.Unit?> = context.settingsDataStore.data.map { preferences ->
        preferences[LAST_UNIT_KEY]?.let { stored ->
            runCatching { UnitPrice.Unit.valueOf(stored) }.getOrNull()
        }
    }

    suspend fun setLastUnit(unit: UnitPrice.Unit) {
        context.settingsDataStore.edit { preferences ->
            preferences[LAST_UNIT_KEY] = unit.name
        }
    }

    private companion object {
        val LAST_UNIT_KEY = stringPreferencesKey("last_unit")
    }
}
