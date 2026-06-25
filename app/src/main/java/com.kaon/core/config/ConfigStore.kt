package com.kaon.core.config

interface ConfigStore {
    fun getString(key: String, default: String): String
    fun setString(key: String, value: String)
}
