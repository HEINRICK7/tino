package com.tino.app.domain.onboarding

data class BootstrapBusiness(
    val id: String,
    val tradeName: String,
    val vertical: String,
    val status: String?,
    val role: String?,
    val dataSourceType: String? = null,
)

data class BootstrapInstallation(
    val id: String,
    val installationId: String,
    val businessId: String,
    val status: String?,
)

data class BootstrapContext(
    val state: String,
    val businesses: List<BootstrapBusiness>,
    val selectedBusiness: BootstrapBusiness?,
    val installation: BootstrapInstallation?,
)

data class OnboardingResult(
    val business: BootstrapBusiness,
    val installation: BootstrapInstallation,
    val dataSource: BusinessDataSource,
)

enum class BusinessDataSourceType {
    TINO_NATIVE,
    EXTERNAL_API,
}

data class BusinessDataSource(
    val businessId: String,
    val sourceType: BusinessDataSourceType,
    val provider: String?,
    val connectionId: String?,
    val status: String?,
)

data class OnboardingDataSourceChoice(
    val sourceType: BusinessDataSourceType,
    val provider: String? = null,
) {
    init {
        when (sourceType) {
            BusinessDataSourceType.TINO_NATIVE -> require(provider == null)
            BusinessDataSourceType.EXTERNAL_API -> require(provider == DOCES_SONHOS_PROVIDER)
        }
    }

    companion object {
        const val DOCES_SONHOS_PROVIDER = "DOCES_SONHOS"

        val Native = OnboardingDataSourceChoice(BusinessDataSourceType.TINO_NATIVE)
        val DocesSonhos = OnboardingDataSourceChoice(BusinessDataSourceType.EXTERNAL_API, DOCES_SONHOS_PROVIDER)
    }
}

sealed interface OnboardingState {
    data object Idle : OnboardingState
    data object Authenticating : OnboardingState
    data class AwaitingOtp(val challenge: OtpChallenge) : OnboardingState
    data object WhatsAppConfirmed : OnboardingState
    data object LoadingBusiness : OnboardingState
    data object RegisteringInstallation : OnboardingState
    data object Ready : OnboardingState
    data class Error(val message: String) : OnboardingState
}
