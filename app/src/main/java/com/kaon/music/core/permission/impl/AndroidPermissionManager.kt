package com.kaon.music.core.permission.impl

import com.kaon.music.core.permission.PermissionManager

class AndroidPermissionManager : PermissionManager {
    override fun hasPermission(permission: String): Boolean {
        return false
    }

    override fun requestPermission(permission: String) {
        // Implementation
    }
}
