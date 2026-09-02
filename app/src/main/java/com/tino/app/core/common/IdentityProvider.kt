package com.tino.app.core.common

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class InstallationIdentity(
    val storeId: String,
    val deviceId: String,
    val businessId: String? = null,
    val installationId: String = deviceId,
)

@Singleton
class IdentityProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences("tino_identity", Context.MODE_PRIVATE)
    private val _businessId = MutableStateFlow(preferences.getString("business_id", null))
    val businessId: StateFlow<String?> = _businessId.asStateFlow()

    fun current(): InstallationIdentity {
        val storeId = preferences.getString("store_id", null) ?: UuidV7.new().also {
            preferences.edit().putString("store_id", it).apply()
        }
        val deviceId = preferences.getString("device_id", null) ?: UuidV7.new().also {
            preferences.edit().putString("device_id", it).apply()
        }
        val installationId = preferences.getString("installation_id", null) ?: UuidV7.new().also {
            preferences.edit().putString("installation_id", it).apply()
        }
        return InstallationIdentity(
            storeId = storeId,
            deviceId = deviceId,
            businessId = preferences.getString("business_id", null),
            installationId = installationId,
        )
    }

    fun setBusinessId(businessId: String) {
        require(businessId.isNotBlank()) { "O identificador remoto da empresa não pode ser vazio." }
        preferences.edit().putString("business_id", businessId).remove("pending_business_id").apply()
        _businessId.value = businessId
    }

    fun pendingBusinessId(): String? = preferences.getString("pending_business_id", null)

    fun setPendingBusinessId(businessId: String) {
        require(businessId.isNotBlank()) { "O identificador pendente da empresa não pode ser vazio." }
        preferences.edit().putString("pending_business_id", businessId).apply()
    }
}
