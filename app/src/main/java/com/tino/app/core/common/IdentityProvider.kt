package com.tino.app.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class InstallationIdentity(val storeId: String, val deviceId: String)

@Singleton
class IdentityProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("tino_identity", Context.MODE_PRIVATE)

    fun current(): InstallationIdentity {
        val storeId = preferences.getString("store_id", null) ?: UuidV7.new().also {
            preferences.edit().putString("store_id", it).apply()
        }
        val deviceId = preferences.getString("device_id", null) ?: UuidV7.new().also {
            preferences.edit().putString("device_id", it).apply()
        }
        return InstallationIdentity(storeId, deviceId)
    }
}
