/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.structure

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate

/**
 * Data-driven, client-side application of structure processors for the CobblePedia preview.
 *
 * Forward-compatibility is the whole point of this object: nothing here names a specific structure.
 * It auto-indexes every bundled `template_pool` / `configured_feature` JSON to learn which processor
 * list each structure element uses, loads that list generically via the registered processor codecs,
 * and runs the processors over the parsed blocks. Adding a new `.nbt` + processor list + data wiring
 * therefore "just works" with no code change here.
 *
 * Only *world-placement* processors are excluded (they would gut a static preview — e.g. `height_range`
 * culls every block because the template sits below sea level). Every other processor — including future
 * block-substitution ones — applies automatically. All work is wrapped defensively: any failure, or a
 * result that would empty the preview, falls back to the unprocessed blocks.
 */
object StructurePreviewProcessors {

    /**
     * Processor types skipped in preview because they depend on real world placement (height, terrain)
     * and would corrupt or empty a free-floating template. Everything else is applied.
     */
    private val EXCLUDED_TYPES: Set<ResourceLocation> = setOf(
        cobblemonResource("height_range"),
        ResourceLocation.withDefaultNamespace("gravity"),
        ResourceLocation.withDefaultNamespace("jigsaw_replacement") // jigsaws are already resolved in the loader
    )

    /** element/structure location (e.g. `cobblemon:habitats/berry_patch1`) -> processor list id. */
    private val processorListByElement: Map<ResourceLocation, ResourceLocation> by lazy { buildElementIndex() }

    /** The processor list id wired to a given structure element, if any (used to resolve its loot). */
    fun processorListIdFor(elementLocation: ResourceLocation): ResourceLocation? = processorListByElement[elementLocation]

    private val processorListCache = HashMap<ResourceLocation, List<StructureProcessor>>()

    /**
     * Returns [blocks] with the structure element's processors applied, or the input unchanged if no
     * processors are wired, none are applicable, or anything fails.
     *
     * [reroll] varies the randomization: it seeds the `pivot` (which re-picks the per-structure block
     * pair, e.g. the berry type) and offsets each block's `pos` (which re-rolls which positions are
     * substituted). Passing the same reroll yields the same layout; bumping it re-rolls the preview.
     */
    fun apply(elementLocation: ResourceLocation, blocks: List<StructurePreview.BlockEntry>, reroll: Int): List<StructurePreview.BlockEntry> {
        if (blocks.isEmpty()) return blocks
        return try {
            val listId = processorListByElement[elementLocation] ?: return blocks
            val processors = loadProcessors(listId)
            if (processors.isEmpty()) return blocks

            val level = Minecraft.getInstance().level ?: return blocks
            val settings = StructurePlaceSettings()
            // Constant within a pass; varying it between rerolls re-picks the structure's block pair.
            val pivot = BlockPos(reroll, 0, 0)
            val posOffset = reroll * 31

            val originals = blocks.map { StructureTemplate.StructureBlockInfo(it.pos, it.state, null) }
            val current = originals.toMutableList<StructureTemplate.StructureBlockInfo?>()

            for (processor in processors) {
                for (i in current.indices) {
                    val info = current[i] ?: continue
                    // Per-block position drives which blocks match the substitution rules (spatial variety);
                    // the reroll offset shifts that selection so a refresh re-rolls the placements too.
                    val pos = originals[i].pos.offset(posOffset, 0, 0)
                    current[i] = try {
                        processor.processBlock(level, pos, pivot, originals[i], info, settings)
                    } catch (e: Exception) {
                        info
                    }
                }
            }

            val processed = current.asSequence()
                .filterNotNull()
                .filter { !it.state.isAir }
                .map { StructurePreview.BlockEntry(it.pos, it.state) }
                .toList()

            // Guard: never let processing wipe out a structure that had visible blocks.
            if (processed.isEmpty()) blocks else processed
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Structure-preview processor application failed for {}", elementLocation, e)
            blocks
        }
    }

    private fun loadProcessors(listId: ResourceLocation): List<StructureProcessor> = processorListCache.getOrPut(listId) {
        try {
            val resource = "data/${listId.namespace}/worldgen/processor_list/${listId.path}.json"
            val stream = StructurePreviewResources.openStream(resource) ?: return@getOrPut emptyList()
            val json = stream.use { JsonParser.parseReader(it.reader()) }.asJsonObject
            val array = json.getAsJsonArray("processors") ?: return@getOrPut emptyList()
            val registryAccess = Minecraft.getInstance().level?.registryAccess() ?: return@getOrPut emptyList()
            val ops = RegistryOps.create(JsonOps.INSTANCE, registryAccess)
            array.mapNotNull { element ->
                // Skip world-placement processors by their declared type before deserializing
                // (StructureProcessor.getType() is protected, so the JSON field is the reliable filter).
                val typeId = (element as? JsonObject)?.get("processor_type")
                    ?.takeIf { it.isJsonPrimitive }?.asString
                    ?.let { ResourceLocation.parse(it) }
                if (typeId != null && typeId in EXCLUDED_TYPES) return@mapNotNull null
                StructureProcessorType.SINGLE_CODEC.parse(ops, element).result().orElse(null)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.warn("Failed to load processor list {}", listId, e)
            emptyList()
        }
    }

    private fun buildElementIndex(): Map<ResourceLocation, ResourceLocation> {
        val index = HashMap<ResourceLocation, ResourceLocation>()

        // template_pool: each single_pool_element has a "location" (.nbt id) and a "processors" id.
        for (resource in StructurePreviewResources.listResources("data", ".json")) {
            if ("/worldgen/template_pool/" !in "/$resource") continue
            runCatching {
                val root = readJson(resource) ?: return@runCatching
                val elements = root.getAsJsonArray("elements") ?: return@runCatching
                for (entry in elements) {
                    val element = entry.asJsonObject.getAsJsonObject("element") ?: continue
                    val location = element.get("location")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                    val processors = element.get("processors")?.takeIf { it.isJsonPrimitive }?.asString ?: continue
                    index.putIfAbsent(ResourceLocation.parse(location), ResourceLocation.parse(processors))
                }
            }
        }

        // configured_feature: cobblemon "structure" features list their .nbt ids + a shared processors id.
        for (resource in StructurePreviewResources.listResources("data", ".json")) {
            if ("/worldgen/configured_feature/" !in "/$resource") continue
            runCatching {
                val root = readJson(resource) ?: return@runCatching
                val config = root.getAsJsonObject("config") ?: return@runCatching
                val processors = config.get("cobblemon_processors")?.takeIf { it.isJsonPrimitive }?.asString ?: return@runCatching
                val structures = config.getAsJsonArray("cobblemon_structures") ?: return@runCatching
                val processorsId = ResourceLocation.parse(processors)
                for (structure in structures) {
                    if (structure.isJsonPrimitive) index.putIfAbsent(ResourceLocation.parse(structure.asString), processorsId)
                }
            }
        }

        return index
    }

    private fun readJson(resource: String): JsonObject? {
        val stream = StructurePreviewResources.openStream(resource) ?: return null
        return stream.use { JsonParser.parseReader(it.reader()) }.asJsonObject
    }
}
