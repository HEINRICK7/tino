package com.tino.app.domain.profile

/**
 * Business identity choices. They are composable and intentionally separate
 * from UI modules and from the backend's legacy singular vertical.
 */
enum class BusinessActivity(
    val displayName: String,
) {
    MERCADINHO("Mercadinho"),
    ACOUGUE("Açougue"),
    VERDUREIRA("Verdureira"),
    PADARIA("Padaria"),
    CONFEITARIA("Confeitaria"),
    RESTAURANTE("Restaurante"),
    LANCHONETE("Lanchonete"),
    SALAO_BELEZA("Salão de beleza"),
    OFICINA("Oficina"),
    OTHER("Outro"),
}
