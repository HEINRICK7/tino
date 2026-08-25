package com.tino.app.domain.language

import com.tino.app.core.database.CustomerEntity
import com.tino.app.core.database.ProductEntity
import com.tino.app.core.database.SupplierEntity
import com.tino.app.domain.commerce.EntityResolutionMatch
import com.tino.app.domain.commerce.EntityResolutionService
import com.tino.app.domain.commerce.EntityResolutionStrategy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerEntityResolver @Inject constructor(
    private val service: EntityResolutionService,
) : LanguageEntityResolver<CustomerEntity> {
    override suspend fun resolve(reference: String): LanguageEntityResolution<CustomerEntity> =
        service.resolveCustomer(reference).toLanguageResolution("cliente")
}

@Singleton
class ProductEntityResolver @Inject constructor(
    private val service: EntityResolutionService,
) : LanguageEntityResolver<ProductEntity> {
    override suspend fun resolve(reference: String): LanguageEntityResolution<ProductEntity> =
        service.resolveProduct(reference).toLanguageResolution("produto")
}

@Singleton
class SupplierEntityResolver @Inject constructor(
    private val service: EntityResolutionService,
) : LanguageEntityResolver<SupplierEntity> {
    override suspend fun resolve(reference: String): LanguageEntityResolution<SupplierEntity> =
        service.resolveSupplier(reference).toLanguageResolution("fornecedor")
}

private fun <T> EntityResolutionMatch<T>.toLanguageResolution(entityLabel: String): LanguageEntityResolution<T> = when (this) {
    is EntityResolutionMatch.Resolved -> if (strategy == EntityResolutionStrategy.FUZZY) {
        LanguageEntityResolution.NeedsClarification(
            "Encontrei uma aproximação para este $entityLabel. Confirme o nome exato antes de continuar.",
        )
    } else {
        LanguageEntityResolution.Resolved(value)
    }
    is EntityResolutionMatch.Ambiguous -> LanguageEntityResolution.Ambiguous(
        values,
        reason = "Encontrei mais de um $entityLabel com esse nome.",
    )
    EntityResolutionMatch.NotFound -> LanguageEntityResolution.NotFound
}
