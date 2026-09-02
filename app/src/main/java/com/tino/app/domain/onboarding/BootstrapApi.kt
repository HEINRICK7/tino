package com.tino.app.domain.onboarding

interface BootstrapApi {
    suspend fun bootstrap(
        requestedBusinessId: String? = null,
        installationExternalId: String? = null,
    ): BootstrapContext

    suspend fun createBusiness(tradeName: String, vertical: String): BootstrapBusiness

    suspend fun registerInstallation(businessId: String, installationId: String): BootstrapInstallation
}
