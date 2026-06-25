package com.kaon.core.permission.impl

import com.kaon.core.permission.PermissionManager

class AndroidPermissionManager : PermissionManager {
    override fun hasPermission(permission: String): Boolean {
        return false
    }

    override fun requestPermission(permission: String) {
        // Implementation
    }
}
