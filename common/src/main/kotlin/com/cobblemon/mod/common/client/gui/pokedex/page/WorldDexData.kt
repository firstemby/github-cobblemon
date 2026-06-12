/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.structure.StructurePreviewLoader
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources
import com.cobblemon.mod.common.world.feature.CobblemonPlacedFeatures
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation

/**
 * Builds the WorldDex listing: biome groups on the left, each containing the structures and placed
 * features that can generate there, grouped by type. Fully data-driven from the same two places the
 * game itself uses, so new content appears automatically:
 *  - jigsaw structures: the `biomes` field of every bundled worldgen structure JSON,
 *  - placed features: the biome tag recorded per feature in [CobblemonPlacedFeatures.FEATURE_BIOME_TAGS].
 *
 * Numbered .nbt families (berry_patch1..N) collapse into one entry whose variations are cycled in the
 * detail pane via [StructurePreviewLoader.variantsFor].
 */
object WorldDexData {

    enum class EntryType(val order: Int, val langKey: String, val fallback: String) {
        HABITAT(0, "habitats", "Habitats"),
        FOSSIL(1, "fossils", "Fossils"),
        RUIN(2, "ruins", "Ruins"),
        STRUCTURE(3, "structures", "Structures");

        val label: MutableComponent
            get() = Component.translatableWithFallback("cobblemon.ui.worlddex.type.$langKey", fallback)
    }

    class Entry(val id: ResourceLocation, val type: EntryType, private val fallbackVariants: (() -> List<ResourceLocation>)? = null) {
        /**
         * The entry's previewable .nbt variants. Name-based family matching first; when the .nbt files
         * are named differently than the worldgen id, the data-defined fallback resolves them (template
         * pool elements for jigsaw structures, configured-feature structure lists for placed features).
         */
        val variants: List<ResourceLocation> by lazy {
            StructurePreviewLoader.variantsFor(id).ifEmpty { fallbackVariants?.invoke() ?: emptyList() }
        }
        val name: MutableComponent
            get() {
                val base = id.path.substringAfterLast('/')
                val fallback = base.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                return Component.translatableWithFallback("cobblemon.ui.worlddex.entry.${id.namespace}.${id.path.replace('/', '.')}", fallback)
            }
    }

    class BiomeGroup(val tagId: ResourceLocation, val entries: List<Entry>) {
        val name: MutableComponent
            get() {
                val base = tagId.path.substringAfterLast('/').removePrefix("is_").removePrefix("has_")
                val fallback = base.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                return Component.translatableWithFallback("cobblemon.ui.worlddex.biome.${tagId.namespace}.${tagId.path.replace('/', '.')}", fallback)
            }
    }

    val groups: List<BiomeGroup> by lazy {
        runCatching { build() }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to build WorldDex data", it)
            emptyList()
        }
    }

    val totalEntries: Int get() = groups.sumOf { it.entries.size }

    private fun build(): List<BiomeGroup> {
        // tag id -> (family key -> entry); family keying dedupes numbered variants into one entry.
        val byTag = LinkedHashMap<ResourceLocation, LinkedHashMap<String, Entry>>()

        fun put(tagId: ResourceLocation, entry: Entry) {
            byTag.getOrPut(tagId) { LinkedHashMap() }.putIfAbsent(entry.id.toString(), entry)
        }

        // Jigsaw structures: read each bundled structure JSON's "biomes" field (string or array).
        // The WorldDex is Cobblemon content only — vanilla (and other mods') structures are skipped.
        for (resource in StructurePreviewResources.listResources("data", ".json")) {
            if ("/worldgen/structure/" !in "/$resource") continue
            runCatching {
                val ns = resource.substringAfter("data/").substringBefore('/')
                if (ns != "cobblemon") return@runCatching
                val rel = resource.substringAfter("/worldgen/structure/").removeSuffix(".json")
                val stream = StructurePreviewResources.openStream(resource) ?: return@runCatching
                val json = stream.use { JsonParser.parseReader(it.reader()) }.asJsonObject
                val biomes = json.get("biomes") ?: return@runCatching
                val tagStrings = when {
                    biomes.isJsonArray -> biomes.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                    biomes.isJsonPrimitive -> listOf(biomes.asString)
                    else -> emptyList()
                }
                if (tagStrings.isEmpty()) return@runCatching
                val familyPath = stripTrailingDigits(rel)
                val startPool = json.get("start_pool")?.takeIf { it.isJsonPrimitive }
                    ?.let { ResourceLocation.tryParse(it.asString) }
                val fallback = startPool?.let { pool -> { poolVariants(pool) } }
                val entry = Entry(ResourceLocation.fromNamespaceAndPath(ns, familyPath), typeFor(familyPath) ?: EntryType.STRUCTURE, fallback)
                for (tag in tagStrings) {
                    put(ResourceLocation.parse(tag.removePrefix("#")), entry)
                }
            }
        }

        // Placed features: the biome tag recorded at registration. Only world structures are listed
        // (habitats/fossils/ruins); vegetation features (apricorns, mints, ...) are not WorldDex content.
        for ((featureKey, tag) in CobblemonPlacedFeatures.FEATURE_BIOME_TAGS) {
            val id = featureKey.location()
            val type = typeFor(id.path) ?: continue
            val tagId = tag?.location() ?: continue
            put(tagId, Entry(id, type) { featureVariants(id) })
        }

        // Entries with no resolvable .nbt at all (e.g. vanilla code-generated structures like buried
        // treasure or mineshafts) have nothing to preview, so they are hidden from the listing.
        return byTag.mapNotNull { (tagId, entries) ->
            val previewable = entries.values
                .filter { it.variants.isNotEmpty() }
                .sortedWith(compareBy({ it.type.order }, { it.id.path }))
            if (previewable.isEmpty()) null else BiomeGroup(tagId, previewable)
        }.sortedBy { it.name.string }
    }

    private fun typeFor(path: String): EntryType? = when {
        path.startsWith("habitats/") -> EntryType.HABITAT
        path.startsWith("fossils/") -> EntryType.FOSSIL
        path.startsWith("ruins/") -> EntryType.RUIN
        else -> null
    }

    private val poolVariantCache = HashMap<ResourceLocation, List<ResourceLocation>>()

    /** The previewable .nbt element locations a template pool actually places. */
    private fun poolVariants(pool: ResourceLocation): List<ResourceLocation> = poolVariantCache.getOrPut(pool) {
        runCatching {
            val json = readJson("data/${pool.namespace}/worldgen/template_pool/${pool.path}.json")
                ?: return@runCatching emptyList()
            val elements = json.getAsJsonArray("elements") ?: return@runCatching emptyList()
            elements.mapNotNull { entry ->
                entry.takeIf { it.isJsonObject }?.asJsonObject
                    ?.getAsJsonObject("element")?.get("location")
                    ?.takeIf { it.isJsonPrimitive }?.asString
            }
                .mapNotNull { ResourceLocation.tryParse(it) }
                .distinct()
                .filter { StructurePreviewLoader.hasTemplate(it) }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to read template pool {} for WorldDex previews", pool, it)
            emptyList()
        }
    }

    private val featureVariantCache = HashMap<ResourceLocation, List<ResourceLocation>>()

    /**
     * Resolves a placed feature's .nbt list through its data chain: the placed_feature JSON's `feature`
     * reference (id or inline object) to the configured_feature, whose `config.cobblemon_structures`
     * names the actual templates. This is what catches features whose .nbts are named differently than
     * the feature (e.g. `habitats/sandy_tidepools` placing `habitats/sandy_tidepool1..3`).
     */
    private fun featureVariants(featureId: ResourceLocation): List<ResourceLocation> = featureVariantCache.getOrPut(featureId) {
        runCatching {
            val placed = readJson("data/${featureId.namespace}/worldgen/placed_feature/${featureId.path}.json")
            val featureElement = placed?.get("feature")
            val configured: JsonObject? = when {
                featureElement == null ->
                    readJson("data/${featureId.namespace}/worldgen/configured_feature/${featureId.path}.json")
                featureElement.isJsonPrimitive ->
                    ResourceLocation.tryParse(featureElement.asString)?.let {
                        readJson("data/${it.namespace}/worldgen/configured_feature/${it.path}.json")
                    }
                featureElement.isJsonObject -> featureElement.asJsonObject
                else -> null
            }
            val config = configured?.getAsJsonObject("config") ?: return@runCatching emptyList()
            val structures = config.getAsJsonArray("cobblemon_structures") ?: return@runCatching emptyList()
            structures.mapNotNull { element ->
                element.takeIf { it.isJsonPrimitive }?.asString?.let(ResourceLocation::tryParse)
            }
                .distinct()
                .filter { StructurePreviewLoader.hasTemplate(it) }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to resolve placed feature {} for WorldDex previews", featureId, it)
            emptyList()
        }
    }

    private fun readJson(resource: String): JsonObject? {
        val stream = StructurePreviewResources.openStream(resource) ?: return null
        return stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun stripTrailingDigits(value: String): String = value.replace(Regex("\\d+$"), "")
}
