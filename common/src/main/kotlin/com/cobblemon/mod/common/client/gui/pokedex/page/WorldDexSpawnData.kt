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
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.core.Registry
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.level.biome.Biome

/**
 * Pokémon spawn listing for the WorldDex detail pane. The bundled spawn_pool_world JSONs are indexed
 * once (species, rarity bucket, biome/structure conditions); a WorldDex entry then matches spawns whose
 *  - `structures` condition names the entry's structure id directly, or
 *  - `biomes` condition overlaps the entry's biome group, resolved through the client's synced biome
 *    registry so tag nesting (e.g. a spawn's is_forest inside a group's is_temperate) matches correctly.
 */
object WorldDexSpawnData {

    class SpawnEntry(val species: String, val bucket: String, val weight: Double) {
        val name: MutableComponent
            get() {
                val fallback = species.replace('_', ' ').replaceFirstChar(Char::uppercase)
                return Component.translatableWithFallback("cobblemon.species.$species.name", fallback)
            }
        val bucketLabel: String
            get() = bucket.replace('-', ' ').split(' ').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
    }

    private class RawSpawn(val species: String, val bucket: String, val weight: Double, val biomes: List<String>, val structures: List<String>)

    private val BUCKET_ORDER = mapOf("common" to 0, "uncommon" to 1, "rare" to 2, "ultra-rare" to 3)

    /** Display ordering for rarity buckets (common first, unknown buckets last). */
    fun bucketOrder(bucket: String): Int = BUCKET_ORDER[bucket] ?: 9

    private val rawSpawns: List<RawSpawn> by lazy {
        runCatching { load() }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to index spawn pool data for the WorldDex", it)
            emptyList()
        }
    }

    private val tagCache = HashMap<String, Set<ResourceLocation>>()
    private val resultCache = HashMap<String, List<SpawnEntry>>()

    /** Spawns relevant to [entryId] within biome group [groupTag], sorted by rarity then name. */
    fun spawnsFor(groupTag: ResourceLocation?, entryId: ResourceLocation): List<SpawnEntry> {
        val registry = Minecraft.getInstance().level?.registryAccess()?.registryOrThrow(Registries.BIOME)
            ?: return emptyList()
        return resultCache.getOrPut("$groupTag|$entryId") {
            val groupBiomes = groupTag?.let { expand(registry, "#$it") } ?: emptySet()
            val entryIdStr = entryId.toString()
            rawSpawns.asSequence()
                .filter { spawn ->
                    spawn.structures.any { it.removePrefix("#") == entryIdStr } ||
                        (groupBiomes.isNotEmpty() && spawn.biomes.any { ref ->
                            expand(registry, ref).any(groupBiomes::contains)
                        })
                }
                // A species can have several matching spawn entries in a bucket; sum their weights so the
                // per-bucket percentage reflects its total share.
                .groupBy { it.species to it.bucket }
                .map { (key, spawns) -> SpawnEntry(key.first, key.second, spawns.sumOf { it.weight }) }
                .sortedWith(compareBy({ bucketOrder(it.bucket) }, { -it.weight }, { it.species }))
        }
    }

    /** Resolves a biome reference ("#ns:tag" or "ns:biome") to the concrete biome ids it covers. */
    private fun expand(registry: Registry<Biome>, ref: String): Set<ResourceLocation> = tagCache.getOrPut(ref) {
        if (ref.startsWith("#")) {
            val tagLocation = ResourceLocation.tryParse(ref.removePrefix("#")) ?: return@getOrPut emptySet()
            registry.getTag(TagKey.create(Registries.BIOME, tagLocation))
                .map { named -> named.mapNotNull { holder -> holder.unwrapKey().orElse(null)?.location() }.toSet() }
                .orElse(emptySet())
        } else {
            ResourceLocation.tryParse(ref)?.let { setOf(it) } ?: emptySet()
        }
    }

    private fun load(): List<RawSpawn> {
        val result = ArrayList<RawSpawn>()
        for (resource in StructurePreviewResources.listResources("data", ".json")) {
            if ("/spawn_pool_world/" !in "/$resource") continue
            runCatching {
                val stream = StructurePreviewResources.openStream(resource) ?: return@runCatching
                val root = stream.use { JsonParser.parseReader(it.reader()) }
                if (!root.isJsonObject) return@runCatching
                val rootObj = root.asJsonObject
                // Files are either a wrapper { "spawns": [...] } or a single spawn detail object.
                when {
                    rootObj.has("spawns") -> rootObj.getAsJsonArray("spawns").forEach { spawn ->
                        if (spawn.isJsonObject) parseSpawn(spawn.asJsonObject)?.let(result::add)
                    }
                    else -> parseSpawn(rootObj)?.let(result::add)
                }
            }
        }
        return result
    }

    private fun parseSpawn(obj: JsonObject): RawSpawn? {
        val pokemon = obj.get("pokemon")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
        // The pokemon field may carry property arguments ("magikarp level=5"); the species is the first token.
        val species = pokemon.substringBefore(' ').lowercase()
        if (species.isEmpty()) return null
        val bucket = obj.get("bucket")?.takeIf { it.isJsonPrimitive }?.asString ?: "common"
        val weight = obj.get("weight")?.takeIf { it.isJsonPrimitive }?.asDouble ?: 1.0
        val condition = obj.get("condition")?.takeIf { it.isJsonObject }?.asJsonObject
        return RawSpawn(
            species = species,
            bucket = bucket,
            weight = weight,
            biomes = stringList(condition, "biomes"),
            structures = stringList(condition, "structures")
        )
    }

    private fun stringList(obj: JsonObject?, key: String): List<String> {
        val array = obj?.get(key)?.takeIf { it.isJsonArray }?.asJsonArray ?: return emptyList()
        return array.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
    }
}
