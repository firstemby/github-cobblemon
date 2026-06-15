/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.structure.StructurePreviewProcessors
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import net.minecraft.resources.ResourceLocation

/**
 * Resolves the loot a Ruin/Fossil can yield by reading its structure processor list — the same data
 * that places the loot in-world. A processor rule that converts a block into a chest/suspicious block
 * attaches an `append_loot` modifier naming a loot table; we collect those, split them by whether the
 * output block is a suspicious (archaeology) block, and expand each loot table into its item ids.
 */
object WorldDexLoot {

    class LootInfo(val chestItems: List<ResourceLocation>, val susItems: List<ResourceLocation>) {
        val isEmpty: Boolean get() = chestItems.isEmpty() && susItems.isEmpty()
    }

    private val cache = HashMap<WorldDexData.Entry, LootInfo>()

    fun lootFor(entry: WorldDexData.Entry): LootInfo = cache.getOrPut(entry) {
        runCatching { resolve(entry) }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to resolve loot for {}", entry.id, it)
            LootInfo(emptyList(), emptyList())
        }
    }

    private fun resolve(entry: WorldDexData.Entry): LootInfo {
        val chestTables = LinkedHashSet<ResourceLocation>()
        val susTables = LinkedHashSet<ResourceLocation>()

        // Each variant element may carry its own processor list; union their loot references.
        for (variant in entry.variants) {
            val listId = StructurePreviewProcessors.processorListIdFor(variant) ?: continue
            val json = WorldDexData.readJson("data/${listId.namespace}/worldgen/processor_list/${listId.path}.json") ?: continue
            val processors = json.getAsJsonArray("processors") ?: continue
            for (processor in processors) {
                collectLootRefs(processor.takeIf { it.isJsonObject }?.asJsonObject ?: continue, chestTables, susTables)
            }
        }

        return LootInfo(
            expandAll(chestTables),
            expandAll(susTables)
        )
    }

    /** Walks a processor object (rules, and minecraft:capped delegates) for output-block + loot pairs. */
    private fun collectLootRefs(processor: JsonObject, chest: MutableSet<ResourceLocation>, sus: MutableSet<ResourceLocation>) {
        processor.getAsJsonObject("delegate")?.let { collectLootRefs(it, chest, sus) }
        val rules = processor.getAsJsonArray("rules") ?: return
        for (rule in rules) {
            val ruleObj = rule.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val lootTable = ruleObj.getAsJsonObject("block_entity_modifier")
                ?.get("loot_table")?.takeIf { it.isJsonPrimitive }?.asString
                ?.let(ResourceLocation::tryParse) ?: continue
            val outputBlock = ruleObj.getAsJsonObject("output_state")?.get("Name")
                ?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
            if ("suspicious" in outputBlock) sus.add(lootTable) else chest.add(lootTable)
        }
    }

    private fun expandAll(tables: Set<ResourceLocation>): List<ResourceLocation> =
        tables.flatMap { expandTable(it, 0) }.distinct()

    /** Item ids dropped by a loot table, following entry children and nested loot-table refs (shallow). */
    private fun expandTable(table: ResourceLocation, depth: Int): List<ResourceLocation> {
        if (depth > 2) return emptyList()
        val json = WorldDexData.readJson("data/${table.namespace}/loot_table/${table.path}.json") ?: return emptyList()
        val items = ArrayList<ResourceLocation>()
        json.getAsJsonArray("pools")?.forEach { pool ->
            pool.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArray("entries")?.let { collectEntries(it, items, depth) }
        }
        return items
    }

    private fun collectEntries(entries: JsonArray, items: MutableList<ResourceLocation>, depth: Int) {
        for (element in entries) {
            val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            when (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString) {
                "minecraft:item" -> obj.get("name")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.let(ResourceLocation::tryParse)?.let(items::add)
                "minecraft:loot_table" -> obj.get("value")?.takeIf { it.isJsonPrimitive }?.asString
                    ?.let(ResourceLocation::tryParse)?.let { items.addAll(expandTable(it, depth + 1)) }
                else -> {} // alternatives/groups/tags: descend into children below
            }
            obj.getAsJsonArray("children")?.let { collectEntries(it, items, depth) }
        }
    }
}
