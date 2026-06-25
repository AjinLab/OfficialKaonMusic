package com.kaon.core.config.impl

import com.kaon.core.config.ConfigStore

class DataStoreConfigStore : ConfigStore {
    override fun getString(key: String, default: String): String {
        return default
    }

    override fun setString(key: String, value: String) {
        // Implementation
    }
}
