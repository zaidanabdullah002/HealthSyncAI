package com.healthsync.ai.model

import android.content.Context
import java.util.UUID

object AppIdentity {
    private const val PREFS = "healthsync_identity"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_DEVICE_ID = "device_id"

    fun getUserId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_USER_ID, null)
        if (existing != null) return existing

        val created = "user-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_USER_ID, created).apply()
        return created
    }

    fun getDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing

        val created = "device-${UUID.randomUUID().toString().take(8)}"
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }
}
