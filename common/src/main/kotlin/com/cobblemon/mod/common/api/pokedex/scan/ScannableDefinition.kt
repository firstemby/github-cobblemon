package com.cobblemon.mod.common.api.pokedex.scan

import net.minecraft.resources.ResourceLocation

/**
 * A datapack-defined inclusion list of what is scannable within one bucket
 * ([category] + optional [subCategory]). Anything not covered by some definition is not scannable.
 *
 * JSON (data/cobblemon/scannable_definitions/pokeballs.json):
 * ```
 * {
 *   "category": "items",
 *   "sub_category": "pokeballs",
 *   "entries": ["cobblemon:poke_ball", "cobblemon:great_ball"],
 *   "tags": ["cobblemon:poke_balls"]
 * }
 * ```
 *
 * @param entries explicit ids that are scannable in this bucket.
 * @param tags item tag ids whose members are scannable in this bucket (items only).
 */
data class ScannableDefinition(
    val category: String,
    val subCategory: String? = null,
    val entries: List<ResourceLocation> = emptyList(),
    val tags: List<ResourceLocation> = emptyList()
) {
    fun bucketPath(): String =
        if (subCategory.isNullOrBlank()) category.lowercase() else "${category.lowercase()}/${subCategory.lowercase()}"
}
