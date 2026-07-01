package com.kaon.music.core.permission

import android.app.Activity

interface PermissionManager {
    fun hasPermission(permission: String): Boolean
    fun requestPermission(activity: Activity, permission: String)
}
