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
    // Nullable because GSON deserialization ignores Kotlin defaults; a missing JSON field stays null.
    val category: String? = null,
    val subCategory: String? = null,
    val entries: List<ResourceLocation>? = null,
    val tags: List<ResourceLocation>? = null
) {
    fun entryIds(): List<ResourceLocation> = entries ?: emptyList()
    fun tagIds(): List<ResourceLocation> = tags ?: emptyList()

    fun bucketPath(): String {
        val cat = (category ?: ScannedDataCategory.MISC.id).lowercase()
        return if (subCategory.isNullOrBlank()) cat else "$cat/${subCategory.lowercase()}"
    }
}
