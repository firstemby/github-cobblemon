package com.cobblemon.mod.common.api.pokedex.scan

/**
 * The logical buckets of "scanned data" tracked per player inside a single [ScannedDataManager].
 * Each is independently synced, persisted, and surfaced in the Pokédex. Adding a new bucket later
 * is a one-line addition here rather than a whole new player data store.
 *
 * The [id] is the stable string used in NBT/JSON, in the network protocol, and as the lang category
 * segment (`cobblemon.ui.pokedex.scanner.<id>.<scanId>.<frameField>`).
 */
enum class ScannedDataCategory(val id: String) {
    BERRIES("berries"),
    EGGS("eggs"),
    BLOCKS("blocks"),
    ITEMS("items"),
    STRUCTURES("structures"),
    HABITATS("habitats"),
    MISC("misc");

    /**
     * The storage bucket path for this category, optionally narrowed to a sub-category.
     * e.g. `ITEMS.bucket("pokeballs")` -> "items/pokeballs", `BERRIES.bucket()` -> "berries".
     */
    fun bucket(sub: String? = null): String =
        if (sub.isNullOrBlank()) id else "$id/${sub.trim().lowercase()}"

    companion object {
        fun fromId(id: String): ScannedDataCategory? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

        /** The top-level category of a bucket path ("items/pokeballs" -> ITEMS). */
        fun categoryOfBucket(bucket: String): ScannedDataCategory? = fromId(bucket.substringBefore('/'))

        /** The sub-category of a bucket path, or null if it has none ("items/pokeballs" -> "pokeballs"). */
        fun subOfBucket(bucket: String): String? = if (bucket.contains('/')) bucket.substringAfter('/') else null
    }
}
