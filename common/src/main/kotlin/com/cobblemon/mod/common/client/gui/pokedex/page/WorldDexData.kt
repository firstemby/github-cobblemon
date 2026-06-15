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
 * The WorldDex catalog of Cobblemon's own worldgen content, organized by category (Habitats, Ruins,
 * Fossils). Fully data-driven so new content appears automatically:
 *  - jigsaw structures: each bundled worldgen structure JSON (its `biomes` tags), and
 *  - placed features: the biome tag recorded per feature in [CobblemonPlacedFeatures.FEATURE_BIOME_TAGS].
 *
 * Numbered/renamed `.nbt` families collapse into one entry (its variations are cycled in the detail
 * pane), and the structure and placed-feature representations of the same thing are deduped by a
 * normalized key. Vanilla content is excluded — this is Cobblemon-only.
 */
object WorldDexData {

    /** The three WorldDex categories shown as tiles. */
    enum class Category(val order: Int, val langKey: String, val fallback: String) {
        HABITATS(0, "habitats", "Habitats"),
        RUINS(1, "ruins", "Ruins"),
        FOSSILS(2, "fossils", "Fossils");

        val label: MutableComponent
            get() = Component.translatableWithFallback("cobblemon.ui.worlddex.category.$langKey", fallback)
    }

    class Entry(
        val id: ResourceLocation,
        val category: Category,
        val biomeTags: Set<ResourceLocation>,
        private val fallbackVariants: (() -> List<ResourceLocation>)? = null
    ) {
        /** The entry's previewable `.nbt` variants (numbered/renamed families resolved). */
        val variants: List<ResourceLocation> by lazy {
            StructurePreviewLoader.variantsFor(id).ifEmpty { fallbackVariants?.invoke() ?: emptyList() }
        }
        val name: MutableComponent
            get() {
                val base = id.path.substringAfterLast('/')
                val fallback = prettify(base)
                return Component.translatableWithFallback("cobblemon.ui.worlddex.entry.${id.namespace}.${id.path.replace('/', '.')}", fallback)
            }
    }

    private val entriesByCategory: Map<Category, List<Entry>> by lazy {
        runCatching { build() }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to build WorldDex data", it)
            emptyMap()
        }
    }

    fun entriesOf(category: Category): List<Entry> = entriesByCategory[category] ?: emptyList()
    fun count(category: Category): Int = entriesOf(category).size

    private class Builder(val id: ResourceLocation, val category: Category, val fallback: (() -> List<ResourceLocation>)?) {
        val tags = LinkedHashSet<ResourceLocation>()
    }

    private fun build(): Map<Category, List<Entry>> {
        // normalized key -> accumulating builder; collapses numbered variants AND the structure/feature
        // representations of the same thing (e.g. ruins/crumbling_arch vs ruins/crumbling_arch_ruins).
        val builders = LinkedHashMap<String, Builder>()

        fun add(id: ResourceLocation, category: Category, tag: ResourceLocation?, fallback: (() -> List<ResourceLocation>)?) {
            val key = "${category.name}:${normalizeKey(id.path)}"
            val builder = builders.getOrPut(key) { Builder(id, category, fallback) }
            tag?.let(builder.tags::add)
        }

        // Jigsaw structures: read each Cobblemon structure JSON's "biomes" field (string or array).
        for (resource in StructurePreviewResources.listResources("data", ".json")) {
            if ("/worldgen/structure/" !in "/$resource") continue
            runCatching {
                val ns = resource.substringAfter("data/").substringBefore('/')
                if (ns != "cobblemon") return@runCatching
                val rel = resource.substringAfter("/worldgen/structure/").removeSuffix(".json")
                val category = categoryFor(rel) ?: return@runCatching
                val json = readJson(resource) ?: return@runCatching
                val biomes = json.get("biomes") ?: return@runCatching
                val tagStrings = when {
                    biomes.isJsonArray -> biomes.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                    biomes.isJsonPrimitive -> listOf(biomes.asString)
                    else -> emptyList()
                }
                val familyPath = stripTrailingDigits(rel)
                val startPool = json.get("start_pool")?.takeIf { it.isJsonPrimitive }?.let { ResourceLocation.tryParse(it.asString) }
                val fallback = startPool?.let { pool -> { poolVariants(pool) } }
                val id = ResourceLocation.fromNamespaceAndPath(ns, familyPath)
                if (tagStrings.isEmpty()) add(id, category, null, fallback)
                else tagStrings.forEach { add(id, category, ResourceLocation.parse(it.removePrefix("#")), fallback) }
            }
        }

        // Placed features: the biome tag recorded at registration.
        for ((featureKey, tag) in CobblemonPlacedFeatures.FEATURE_BIOME_TAGS) {
            val id = featureKey.location()
            val category = categoryFor(id.path) ?: continue
            add(id, category, tag?.location()) { featureVariants(id) }
        }

        val result = LinkedHashMap<Category, MutableList<Entry>>()
        for (builder in builders.values) {
            val entry = Entry(builder.id, builder.category, builder.tags, builder.fallback)
            if (entry.variants.isEmpty()) continue // nothing to preview -> not listed
            result.getOrPut(builder.category) { ArrayList() }.add(entry)
        }
        return result.mapValues { (_, list) -> list.sortedBy { it.name.string } }
    }

    private fun categoryFor(path: String): Category? = when {
        path.startsWith("habitats/") -> Category.HABITATS
        path.startsWith("ruins/") -> Category.RUINS
        path.startsWith("fossils/") -> Category.FOSSILS
        else -> null
    }

    /** Collapses numbered variants, plural/singular drift, and the "_ruins" suffix to one dedup key. */
    private fun normalizeKey(path: String): String {
        var base = stripTrailingDigits(path.substringAfterLast('/'))
        base = base.removeSuffix("_ruins").removeSuffix("_ruin")
        if (base.endsWith("s")) base = base.dropLast(1)
        return base
    }

    private val poolVariantCache = HashMap<ResourceLocation, List<ResourceLocation>>()

    private fun poolVariants(pool: ResourceLocation): List<ResourceLocation> = poolVariantCache.getOrPut(pool) {
        runCatching {
            val json = readJson("data/${pool.namespace}/worldgen/template_pool/${pool.path}.json") ?: return@runCatching emptyList()
            val elements = json.getAsJsonArray("elements") ?: return@runCatching emptyList()
            elements.mapNotNull {
                it.takeIf { e -> e.isJsonObject }?.asJsonObject?.getAsJsonObject("element")?.get("location")
                    ?.takeIf { l -> l.isJsonPrimitive }?.asString
            }.mapNotNull(ResourceLocation::tryParse).distinct().filter { StructurePreviewLoader.hasTemplate(it) }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to read template pool {} for WorldDex", pool, it)
            emptyList()
        }
    }

    private val featureVariantCache = HashMap<ResourceLocation, List<ResourceLocation>>()

    private fun featureVariants(featureId: ResourceLocation): List<ResourceLocation> = featureVariantCache.getOrPut(featureId) {
        runCatching {
            val placed = readJson("data/${featureId.namespace}/worldgen/placed_feature/${featureId.path}.json")
            val featureElement = placed?.get("feature")
            val configured: JsonObject? = when {
                featureElement == null -> readJson("data/${featureId.namespace}/worldgen/configured_feature/${featureId.path}.json")
                featureElement.isJsonPrimitive -> ResourceLocation.tryParse(featureElement.asString)?.let {
                    readJson("data/${it.namespace}/worldgen/configured_feature/${it.path}.json")
                }
                featureElement.isJsonObject -> featureElement.asJsonObject
                else -> null
            }
            val config = configured?.getAsJsonObject("config") ?: return@runCatching emptyList()
            val structures = config.getAsJsonArray("cobblemon_structures") ?: return@runCatching emptyList()
            structures.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString?.let(ResourceLocation::tryParse) }
                .distinct().filter { StructurePreviewLoader.hasTemplate(it) }
        }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to resolve placed feature {} for WorldDex", featureId, it)
            emptyList()
        }
    }

    internal fun readJson(resource: String): JsonObject? {
        val stream = StructurePreviewResources.openStream(resource) ?: return null
        return stream.use { JsonParser.parseReader(it.reader()) }.takeIf { it.isJsonObject }?.asJsonObject
    }

    internal fun prettify(value: String): String =
        value.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    internal fun stripTrailingDigits(value: String): String = value.replace(Regex("\\d+$"), "")
}
