package com.tino.app.domain.onboarding

interface BusinessDataSourceApi {
    suspend fun get(businessId: String): BusinessDataSource

    suspend fun select(
        businessId: String,
        sourceType: BusinessDataSourceType,
        provider: String?,
    ): BusinessDataSource
}
