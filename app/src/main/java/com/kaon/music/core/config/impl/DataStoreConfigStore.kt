package com.kaon.music.core.config.impl

import android.content.Context
import com.kaon.music.core.config.ConfigStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(
    name = "kaon_preferences"
)

class DataStoreConfigStore(
    private val context: Context
) : ConfigStore {

    override suspend fun putString(
        key: String,
        value: String
    ) {
        context.dataStore.edit {
            it[stringPreferencesKey(key)] = value
        }
    }

    override suspend fun putBoolean(
        key: String,
        value: Boolean
    ) {
        context.dataStore.edit {
            it[booleanPreferencesKey(key)] = value
        }
    }

    override suspend fun putInt(
        key: String,
        value: Int
    ) {
        context.dataStore.edit {
            it[intPreferencesKey(key)] = value
        }
    }

    override fun getString(
        key: String,
        defaultValue: String
    ): Flow<String> {
        return context.dataStore.data.map {
            it[stringPreferencesKey(key)] ?: defaultValue
        }
    }

    override fun getBoolean(
        key: String,
        defaultValue: Boolean
    ): Flow<Boolean> {
        return context.dataStore.data.map {
            it[booleanPreferencesKey(key)] ?: defaultValue
        }
    }

    override fun getInt(
        key: String,
        defaultValue: Int
    ): Flow<Int> {
        return context.dataStore.data.map {
            it[intPreferencesKey(key)] ?: defaultValue
        }
    }
}