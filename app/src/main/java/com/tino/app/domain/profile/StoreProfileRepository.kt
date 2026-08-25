package com.tino.app.domain.profile

import com.tino.app.core.database.StoreProfileDao
import com.tino.app.core.database.StoreProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreProfileRepository @Inject constructor(
    private val dao: StoreProfileDao,
) {
    fun observe(): Flow<StoreProfileEntity?> = dao.observe()

    fun observeBusinessProfile(): Flow<BusinessProfile?> = dao.observe().map { it?.toBusinessProfile() }

    suspend fun save(
        storeName: String,
        ownerName: String,
        phone: String,
        vertical: BusinessVertical = BusinessVertical.RETAIL,
        activeModules: Set<BusinessModule> = VerticalPresetCatalog.forVertical(vertical).defaultModules,
    ) {
        require(storeName.isNotBlank()) { "Informe o nome do comércio." }
        require(ownerName.isNotBlank()) { "Informe seu nome." }
        require(phone.filter(Char::isDigit).length in 10..13) { "Informe um celular válido." }
        val profile = BusinessProfile(
            primaryVertical = vertical,
            enabledModules = activeModules,
            storeName = storeName.trim(),
            ownerName = ownerName.trim(),
            phone = phone.trim(),
        )
        dao.upsert(
            StoreProfileEntity(
                id = PROFILE_ID,
                storeName = storeName.trim(),
                ownerName = ownerName.trim(),
                phone = phone.trim(),
                createdAt = System.currentTimeMillis(),
                businessVertical = profile.primaryVertical.name,
                activeModules = profile.enabledModules.joinToString(",") { it.name },
                profileVersion = profile.version,
                operationalPatterns = profile.effectiveOperationalPatterns().joinToString(",") { it.name },
                permanentCapabilities = profile.permanentCapabilities.joinToString(",") { it.name },
            ),
        )
    }

    suspend fun updateProfile(profile: BusinessProfile) {
        BusinessProfileValidator.validate(profile)
        val current = dao.observe().first()
        dao.upsert(
            StoreProfileEntity(
                id = current?.id ?: PROFILE_ID,
                storeName = profile.storeName,
                ownerName = profile.ownerName.orEmpty(),
                phone = profile.phone.orEmpty(),
                createdAt = current?.createdAt ?: System.currentTimeMillis(),
                businessVertical = profile.primaryVertical.name,
                activeModules = profile.enabledModules.joinToString(",") { it.name },
                profileVersion = profile.version,
                operationalPatterns = profile.effectiveOperationalPatterns().joinToString(",") { it.name },
                permanentCapabilities = profile.permanentCapabilities.joinToString(",") { it.name },
            ),
        )
    }

    private fun StoreProfileEntity.toBusinessProfile(): BusinessProfile {
        val vertical = runCatching { BusinessVertical.valueOf(businessVertical) }.getOrDefault(BusinessVertical.RETAIL)
        val modules = activeModules.split(',').mapNotNull { value -> runCatching { BusinessModule.valueOf(value) }.getOrNull() }.toSet()
        val patterns = operationalPatterns.split(',').mapNotNull { value -> runCatching { OperationalPattern.valueOf(value) }.getOrNull() }.toSet()
        val permanent = permanentCapabilities.split(',').mapNotNull { value -> runCatching { com.tino.app.domain.agent.TinoCapabilityId.valueOf(value) }.getOrNull() }.toSet()
        return runCatching {
            BusinessProfile(
                primaryVertical = vertical,
                enabledModules = modules,
                storeName = storeName,
                ownerName = ownerName,
                phone = phone,
                version = profileVersion,
                operationalPatterns = patterns,
                permanentCapabilities = permanent,
            )
        }.getOrElse {
            BusinessProfile(
                primaryVertical = vertical,
                enabledModules = VerticalPresetCatalog.forVertical(vertical).defaultModules,
                storeName = storeName,
                ownerName = ownerName,
                phone = phone,
                operationalPatterns = OperationalPatternCatalog.forVertical(vertical),
            )
        }
    }

    private companion object {
        const val PROFILE_ID = "default"
    }
}
