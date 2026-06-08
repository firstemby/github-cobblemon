package com.cobblemon.mod.common.api.pokedex.scan

object BlockScanDefinition {
    data class Frame(
        val slot: Int,
        val text: ScanText,
        val scale: Float? = null
    )

    sealed interface ScanText {
        data class Literal(val text: String) : ScanText
        data class Key(val key: String) : ScanText
        data class Translate(val key: String, val with: List<ScanText> = emptyList()) : ScanText
    }
}