package com.kaon.music.core.config

import kotlinx.coroutines.flow.Flow

interface ConfigStore {

    suspend fun putString(
        key: String,
        value: String
    )

    suspend fun putBoolean(
        key: String,
        value: Boolean
    )

    suspend fun putInt(
        key: String,
        value: Int
    )

    fun getString(
        key: String,
        defaultValue: String = ""
    ): Flow<String>

    fun getBoolean(
        key: String,
        defaultValue: Boolean = false
    ): Flow<Boolean>

    fun getInt(
        key: String,
        defaultValue: Int = 0
    ): Flow<Int>
}