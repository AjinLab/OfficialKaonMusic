package com.kaon.core.permission

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
    fun requestPermission(permission: String)
}
