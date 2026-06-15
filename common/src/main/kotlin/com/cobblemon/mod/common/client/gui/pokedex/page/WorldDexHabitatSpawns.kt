/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.client.render.structure.StructurePreviewResources
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

/**
 * Reads a habitat's Pokémon spawns from its `data/cobblemon/habitat_pools/<name>.json` file (the same
 * per-habitat spawn pool the game uses). Resolution is name-tolerant so plural/foldered habitat ids
 * still find their pool file. Returns an empty list when no pool exists for the habitat.
 */
object WorldDexHabitatSpawns {

    class SpawnEntry(val species: String, val bucket: String, val weight: Double, val levelRange: String) {
        val name: MutableComponent
            get() {
                val key = species.lowercase()
                return Component.translatableWithFallback("cobblemon.species.$key.name", WorldDexData.prettify(species))
            }
        val bucketLabel: String get() = WorldDexData.prettify(bucket.replace('-', ' '))
    }

    private val BUCKET_ORDER = mapOf("common" to 0, "uncommon" to 1, "rare" to 2, "ultra-rare" to 3)
    fun bucketOrder(bucket: String): Int = BUCKET_ORDER[bucket] ?: 9

    private val cache = HashMap<WorldDexData.Entry, List<SpawnEntry>>()

    fun spawnsFor(entry: WorldDexData.Entry): List<SpawnEntry> = cache.getOrPut(entry) {
        runCatching { load(entry) }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to read habitat spawns for {}", entry.id, it)
            emptyList()
        }
    }

    private fun load(entry: WorldDexData.Entry): List<SpawnEntry> {
        val base = entry.id.path.substringAfterLast('/')
        // Try the bare name and singular/plural variants until a pool file is found.
        val names = LinkedHashSet<String>().apply {
            add(base)
            add(base.removeSuffix("s"))
            add(base + "s")
            add(WorldDexData.stripTrailingDigits(base))
        }
        for (name in names) {
            val json = WorldDexData.readJson("data/${entry.id.namespace}/habitat_pools/$name.json") ?: continue
            val spawns = json.getAsJsonArray("spawns") ?: continue
            return spawns.mapNotNull { element ->
                val obj = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val species = obj.get("species")?.takeIf { it.isJsonPrimitive }?.asString ?: return@mapNotNull null
                SpawnEntry(
                    species = species,
                    bucket = obj.get("bucket")?.takeIf { it.isJsonPrimitive }?.asString ?: "common",
                    weight = obj.get("weight")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 1.0,
                    levelRange = obj.get("levelRange")?.takeIf { it.isJsonPrimitive }?.asString ?: ""
                )
            }.sortedWith(compareBy({ bucketOrder(it.bucket) }, { -it.weight }, { it.species }))
        }
        return emptyList()
    }
}
