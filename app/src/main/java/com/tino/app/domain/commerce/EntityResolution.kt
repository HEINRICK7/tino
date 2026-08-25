package com.tino.app.domain.commerce

sealed interface EntityResolution<out T> {
    data class Resolved<T>(val value: T) : EntityResolution<T>
    data class Ambiguous<T>(val values: List<T>) : EntityResolution<T>
    data object NotFound : EntityResolution<Nothing>
}
