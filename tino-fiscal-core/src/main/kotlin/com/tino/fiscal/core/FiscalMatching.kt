package com.tino.fiscal.core

import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant
import java.util.Locale
import kotlin.math.max

/** A local supplier record exposed to the fiscal matching boundary. */
data class FiscalSupplierCandidate(
    val id: String,
    val taxId: String?,
    val legalName: String?,
    val tradeName: String?,
)

/** A local product record exposed to the fiscal matching boundary. */
data class FiscalProductCandidate(
    val id: String,
    val name: String,
    val gtin: String?,
    val inventoryUnit: String?,
    val currentStock: BigDecimal,
)

data class SupplierProductMapping(
    val supplierId: String,
    val supplierProductCode: String?,
    val gtin: String?,
    val supplierDescription: String,
    val productId: String,
    val confirmedAt: Instant,
    val matchMethod: ProductMatchMethod,
)

data class ProductAlias(
    val normalizedAlias: String,
    val productId: String,
)

enum class SupplierMatchMethod {
    EXACT_TAX_ID,
    NORMALIZED_NAME,
}

enum class ProductMatchMethod {
    EXACT_GTIN,
    SUPPLIER_PRODUCT_MAPPING,
    ALIAS,
    NORMALIZED_DESCRIPTION,
    FUZZY_DESCRIPTION,
}

sealed interface SupplierResolution {
    data class Resolved(
        val supplier: FiscalSupplierCandidate,
        val method: SupplierMatchMethod,
    ) : SupplierResolution

    data class Ambiguous(val candidates: List<FiscalSupplierCandidate>) : SupplierResolution

    data object NotFound : SupplierResolution
}

data class ProductMatchCandidate(
    val product: FiscalProductCandidate,
    val score: Double,
    val method: ProductMatchMethod,
)

sealed interface ProductResolution {
    data class Resolved(
        val product: FiscalProductCandidate,
        val method: ProductMatchMethod,
        val score: Double,
    ) : ProductResolution

    data class Ambiguous(val candidates: List<ProductMatchCandidate>) : ProductResolution

    data object NotFound : ProductResolution
}

/**
 * Supplier matching deliberately gives tax identity precedence over names.
 * A name is only a suggestion when it is unique; it never creates a supplier.
 */
class FiscalSupplierResolver {
    fun resolve(
        issuer: FiscalParty,
        suppliers: List<FiscalSupplierCandidate>,
    ): SupplierResolution {
        val taxId = normalizeTaxId(issuer.taxId)
        if (taxId != null) {
            val taxMatches = suppliers.filter { normalizeTaxId(it.taxId) == taxId }
            return when (taxMatches.size) {
                1 -> SupplierResolution.Resolved(taxMatches.single(), SupplierMatchMethod.EXACT_TAX_ID)
                0 -> when (val nameResolution = resolveByName(issuer, suppliers)) {
                    is SupplierResolution.Resolved -> SupplierResolution.Ambiguous(listOf(nameResolution.supplier))
                    is SupplierResolution.Ambiguous -> nameResolution
                    SupplierResolution.NotFound -> SupplierResolution.NotFound
                }
                else -> SupplierResolution.Ambiguous(taxMatches)
            }
        }
        return resolveByName(issuer, suppliers)
    }

    private fun resolveByName(
        issuer: FiscalParty,
        suppliers: List<FiscalSupplierCandidate>,
    ): SupplierResolution {
        val references = listOfNotNull(issuer.legalName, issuer.tradeName)
            .map(::normalizeText)
            .filter(String::isNotBlank)
            .distinct()
        if (references.isEmpty()) return SupplierResolution.NotFound

        val matches = suppliers.filter { supplier ->
            val names = listOfNotNull(supplier.legalName, supplier.tradeName)
                .map(::normalizeText)
            names.any { it in references }
        }
        return when (matches.size) {
            1 -> SupplierResolution.Resolved(matches.single(), SupplierMatchMethod.NORMALIZED_NAME)
            0 -> SupplierResolution.NotFound
            else -> SupplierResolution.Ambiguous(matches)
        }
    }
}

/**
 * Product matching is ordered from strongest identity to weakest suggestion.
 * Fuzzy results are never silently accepted when confidence or separation is
 * insufficient; the caller receives candidates for human review instead.
 */
class FiscalProductMatcher(
    private val fuzzyResolveThreshold: Double = 0.92,
    private val fuzzyAmbiguityMargin: Double = 0.08,
) {
    fun resolve(
        item: CanonicalFiscalItem,
        supplier: SupplierResolution,
        products: List<FiscalProductCandidate>,
        mappings: List<SupplierProductMapping>,
        aliases: List<ProductAlias>,
    ): ProductResolution {
        val supplierId = (supplier as? SupplierResolution.Resolved)?.supplier?.id
        val gtin = normalizeGtin(item.gtin)
        if (gtin != null) {
            val gtinMatches = products.filter { normalizeGtin(it.gtin) == gtin }
            when (gtinMatches.size) {
                1 -> return ProductResolution.Resolved(
                    product = gtinMatches.single(),
                    method = ProductMatchMethod.EXACT_GTIN,
                    score = 1.0,
                )

                in 2..Int.MAX_VALUE -> return ProductResolution.Ambiguous(
                    gtinMatches.map { ProductMatchCandidate(it, 1.0, ProductMatchMethod.EXACT_GTIN) },
                )
            }
        }

        val supplierCode = item.supplierProductCode?.trim()?.takeIf(String::isNotBlank)
        if (supplierId != null && supplierCode != null) {
            val mapped = mappings.filter {
                it.supplierId == supplierId &&
                    it.supplierProductCode?.trim() == supplierCode
            }
            when (mapped.size) {
                1 -> products.firstOrNull { it.id == mapped.single().productId }?.let {
                    return ProductResolution.Resolved(it, ProductMatchMethod.SUPPLIER_PRODUCT_MAPPING, 1.0)
                }

                in 2..Int.MAX_VALUE -> return mapped.mapNotNull { mapping ->
                    products.firstOrNull { it.id == mapping.productId }
                }.distinctBy { it.id }.let { candidates ->
                    ProductResolution.Ambiguous(
                        candidates.map {
                            ProductMatchCandidate(it, 1.0, ProductMatchMethod.SUPPLIER_PRODUCT_MAPPING)
                        },
                    )
                }
            }
        }

        val normalizedDescription = normalizeText(item.description)
        if (normalizedDescription.isNotBlank()) {
            val aliasMatches = aliases.filter { normalizeText(it.normalizedAlias) == normalizedDescription }
                .mapNotNull { alias -> products.firstOrNull { it.id == alias.productId } }
                .distinctBy { it.id }
            when (aliasMatches.size) {
                1 -> return ProductResolution.Resolved(aliasMatches.single(), ProductMatchMethod.ALIAS, 1.0)
                in 2..Int.MAX_VALUE -> return ProductResolution.Ambiguous(
                    aliasMatches.map { ProductMatchCandidate(it, 1.0, ProductMatchMethod.ALIAS) },
                )
            }

            val descriptionMatches = products.filter { normalizeText(it.name) == normalizedDescription }
            when (descriptionMatches.size) {
                1 -> return ProductResolution.Resolved(
                    descriptionMatches.single(),
                    ProductMatchMethod.NORMALIZED_DESCRIPTION,
                    1.0,
                )

                in 2..Int.MAX_VALUE -> return ProductResolution.Ambiguous(
                    descriptionMatches.map {
                        ProductMatchCandidate(it, 1.0, ProductMatchMethod.NORMALIZED_DESCRIPTION)
                    },
                )
            }
        }

        val fuzzyCandidates = products.map { product ->
            ProductMatchCandidate(
                product = product,
                score = similarity(normalizedDescription, normalizeText(product.name)),
                method = ProductMatchMethod.FUZZY_DESCRIPTION,
            )
        }.filter { it.score > 0.0 }.sortedWith(
            compareByDescending<ProductMatchCandidate> { it.score }.thenBy { it.product.id },
        )
        val top = fuzzyCandidates.firstOrNull() ?: return ProductResolution.NotFound
        val second = fuzzyCandidates.getOrNull(1)
        if (top.score < fuzzyResolveThreshold) return ProductResolution.NotFound
        if (second != null && top.score - second.score < fuzzyAmbiguityMargin) {
            return ProductResolution.Ambiguous(fuzzyCandidates.take(5))
        }
        return ProductResolution.Resolved(top.product, ProductMatchMethod.FUZZY_DESCRIPTION, top.score)
    }
}

sealed interface FiscalImportSupplierPreview {
    data class Existing(
        val supplierId: String,
        val name: String,
        val method: SupplierMatchMethod,
    ) : FiscalImportSupplierPreview

    data class NewSupplier(
        val legalName: String?,
        val tradeName: String?,
        val taxId: String?,
    ) : FiscalImportSupplierPreview

    data class Ambiguous(
        val legalName: String?,
        val tradeName: String?,
        val candidates: List<FiscalSupplierCandidate>,
    ) : FiscalImportSupplierPreview
}

sealed interface FiscalItemImportPreview {
    data class ExistingProduct(
        val productId: String,
        val name: String,
        val incomingQuantity: BigDecimal,
        val fiscalUnit: String?,
        val currentStock: BigDecimal,
        val purchaseUnitValue: BigDecimal,
        val matchMethod: ProductMatchMethod,
        val matchScore: Double,
    ) : FiscalItemImportPreview

    data class NewProduct(
        val supplierDescription: String,
        val gtin: String?,
        val ncm: String?,
        val fiscalQuantity: BigDecimal,
        val fiscalUnit: String?,
        val purchaseUnitValue: BigDecimal,
    ) : FiscalItemImportPreview

    data class AmbiguousProduct(
        val supplierDescription: String,
        val candidates: List<ProductMatchCandidate>,
    ) : FiscalItemImportPreview

    data class PackagingRequired(
        val supplierDescription: String,
        val fiscalQuantity: BigDecimal,
        val fiscalUnit: String,
        val matchedProduct: FiscalProductCandidate,
    ) : FiscalItemImportPreview
}

enum class FiscalImportWarning {
    NEW_SUPPLIER_REQUIRES_CONFIRMATION,
    AMBIGUOUS_SUPPLIER_REQUIRES_SELECTION,
    NEW_PRODUCT_REQUIRES_CONFIRMATION,
    AMBIGUOUS_PRODUCT_REQUIRES_SELECTION,
    PACKAGING_REQUIRES_CONFIRMATION,
}

data class FiscalImportPreview(
    val previewId: String,
    val documentId: String,
    val supplier: FiscalImportSupplierPreview,
    val items: List<FiscalItemImportPreview>,
    val warnings: List<FiscalImportWarning>,
    val canCommit: Boolean,
    val invoiceValue: BigDecimal? = null,
)

/** Builds a review-only result. It never writes or assigns an operational id. */
class FiscalImportPreviewBuilder(
    private val supplierResolver: FiscalSupplierResolver = FiscalSupplierResolver(),
    private val productMatcher: FiscalProductMatcher = FiscalProductMatcher(),
) {
    fun build(
        document: CanonicalFiscalDocument,
        suppliers: List<FiscalSupplierCandidate>,
        products: List<FiscalProductCandidate>,
        mappings: List<SupplierProductMapping> = emptyList(),
        aliases: List<ProductAlias> = emptyList(),
    ): FiscalImportPreview {
        val supplierResolution = supplierResolver.resolve(document.issuer, suppliers)
        val supplierPreview = when (supplierResolution) {
            is SupplierResolution.Resolved -> FiscalImportSupplierPreview.Existing(
                supplierId = supplierResolution.supplier.id,
                name = supplierResolution.supplier.tradeName
                    ?: supplierResolution.supplier.legalName.orEmpty(),
                method = supplierResolution.method,
            )

            is SupplierResolution.Ambiguous -> FiscalImportSupplierPreview.Ambiguous(
                legalName = document.issuer.legalName,
                tradeName = document.issuer.tradeName,
                candidates = supplierResolution.candidates,
            )

            SupplierResolution.NotFound -> FiscalImportSupplierPreview.NewSupplier(
                legalName = document.issuer.legalName,
                tradeName = document.issuer.tradeName,
                taxId = document.issuer.taxId,
            )
        }

        val itemPreviews = document.items.map { item ->
            when (val match = productMatcher.resolve(item, supplierResolution, products, mappings, aliases)) {
                is ProductResolution.Resolved -> {
                    if (unitsDiffer(item.commercialUnit, match.product.inventoryUnit)) {
                        FiscalItemImportPreview.PackagingRequired(
                            supplierDescription = item.description,
                            fiscalQuantity = item.quantity,
                            fiscalUnit = item.commercialUnit.orEmpty(),
                            matchedProduct = match.product,
                        )
                    } else {
                        FiscalItemImportPreview.ExistingProduct(
                            productId = match.product.id,
                            name = match.product.name,
                            incomingQuantity = item.quantity,
                            fiscalUnit = item.commercialUnit,
                            currentStock = match.product.currentStock,
                            purchaseUnitValue = item.unitValue,
                            matchMethod = match.method,
                            matchScore = match.score,
                        )
                    }
                }

                is ProductResolution.Ambiguous -> FiscalItemImportPreview.AmbiguousProduct(
                    supplierDescription = item.description,
                    candidates = match.candidates,
                )

                ProductResolution.NotFound -> FiscalItemImportPreview.NewProduct(
                    supplierDescription = item.description,
                    gtin = item.gtin,
                    ncm = item.ncm,
                    fiscalQuantity = item.quantity,
                    fiscalUnit = item.commercialUnit,
                    purchaseUnitValue = item.unitValue,
                )
            }
        }

        val warnings = buildList {
            when (supplierPreview) {
                is FiscalImportSupplierPreview.NewSupplier -> add(FiscalImportWarning.NEW_SUPPLIER_REQUIRES_CONFIRMATION)
                is FiscalImportSupplierPreview.Ambiguous -> add(FiscalImportWarning.AMBIGUOUS_SUPPLIER_REQUIRES_SELECTION)
                is FiscalImportSupplierPreview.Existing -> Unit
            }
            itemPreviews.forEach { preview ->
                when (preview) {
                    is FiscalItemImportPreview.NewProduct -> add(FiscalImportWarning.NEW_PRODUCT_REQUIRES_CONFIRMATION)
                    is FiscalItemImportPreview.AmbiguousProduct -> add(FiscalImportWarning.AMBIGUOUS_PRODUCT_REQUIRES_SELECTION)
                    is FiscalItemImportPreview.PackagingRequired -> add(FiscalImportWarning.PACKAGING_REQUIRES_CONFIRMATION)
                    is FiscalItemImportPreview.ExistingProduct -> Unit
                }
            }
        }.distinct()

        val hasUnresolvedItem = itemPreviews.any {
            it is FiscalItemImportPreview.AmbiguousProduct ||
                it is FiscalItemImportPreview.PackagingRequired
        }
        val hasAmbiguousSupplier = supplierPreview is FiscalImportSupplierPreview.Ambiguous

        return FiscalImportPreview(
            previewId = "fiscal-preview:${document.id}",
            documentId = document.id,
            supplier = supplierPreview,
            items = itemPreviews,
            warnings = warnings,
            canCommit = !hasUnresolvedItem && !hasAmbiguousSupplier,
            invoiceValue = document.totals.invoiceValue,
        )
    }
}

internal fun normalizeText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    .lowercase(Locale.ROOT)
    .replace("[^a-z0-9]+".toRegex(), " ")
    .trim()
    .replace("\\s+".toRegex(), " ")

private fun normalizeTaxId(value: String?): String? = value
    ?.filter(Char::isDigit)
    ?.takeIf { it.isNotEmpty() }

private fun normalizeGtin(value: String?): String? = value
    ?.trim()
    ?.takeUnless { it.isBlank() || it.equals("SEM GTIN", ignoreCase = true) }

private fun unitsDiffer(fiscalUnit: String?, inventoryUnit: String?): Boolean {
    val fiscal = fiscalUnit?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank)
    val inventory = inventoryUnit?.trim()?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank)
    return fiscal != null && inventory != null && fiscal != inventory
}

private fun similarity(left: String, right: String): Double {
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left == right) return 1.0
    val leftTokens = left.split(' ').filter(String::isNotBlank).toSet()
    val rightTokens = right.split(' ').filter(String::isNotBlank).toSet()
    val union = (leftTokens + rightTokens).size
    val tokenScore = if (union == 0) 0.0 else leftTokens.intersect(rightTokens).size.toDouble() / union
    val distance = levenshtein(left, right)
    val editScore = 1.0 - distance.toDouble() / max(left.length, right.length).toDouble()
    return (tokenScore * 0.65) + (editScore * 0.35)
}

private fun levenshtein(left: String, right: String): Int {
    if (left == right) return 0
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    for (i in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = i + 1
        for (j in right.indices) {
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + if (left[i] == right[j]) 0 else 1,
            )
        }
        previous = current
    }
    return previous[right.length]
}
