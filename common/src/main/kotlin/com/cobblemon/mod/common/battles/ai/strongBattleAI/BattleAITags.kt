/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai.strongBattleAI

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.DataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File

/**
 * Registry for optional tags that can be attached to moves and abilities for StrongBattleAI heuristics.
 */
object BattleAITags : DataRegistry {
    override val id = cobblemonResource("battle_ai_tags")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BattleAITags>()
    private val gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    private val moveTagsById = mutableMapOf<String, MutableSet<String>>()
    private val abilityTagsById = mutableMapOf<String, MutableSet<String>>()
    private val abilityTagMultipliersById = mutableMapOf<String, MutableMap<String, Double>>()

    private val abilityFolders = listOf(
        "battle_ai/abilities",
        "battle_ai/abilties",
        "battleai/abilities",
        "battleai/abilties"
    )
    private val moveFolders = listOf(
        "battle_ai/moves",
        "battleai/moves"
    )
    private const val DEFAULT_BOOST_TAG_MULTIPLIER = 1.5

    override fun sync(player: ServerPlayer) {}

    override fun reload(manager: ResourceManager) {
        moveTagsById.clear()
        abilityTagsById.clear()
        abilityTagMultipliersById.clear()

        loadMoveTags(manager)
        loadAbilityTags(manager)

        Cobblemon.LOGGER.info(
            "Loaded battle AI tags: {} moves, {} abilities, {} ability-tag multipliers",
            moveTagsById.size,
            abilityTagsById.size,
            abilityTagMultipliersById.values.sumOf { it.size }
        )
        observable.emit(this)
    }

    fun getMoveTags(moveId: String?): Set<String> {
        val normalizedMoveId = normalizeId(moveId) ?: return emptySet()
        return moveTagsById[normalizedMoveId]?.toSet() ?: emptySet()
    }

    fun getAbilityTags(abilityId: String?): Set<String> {
        val normalizedAbilityId = normalizeId(abilityId) ?: return emptySet()
        return abilityTagsById[normalizedAbilityId]?.toSet() ?: emptySet()
    }

    fun abilityMoveTagDamageMultiplier(abilityId: String?, moveId: String?): Double {
        val normalizedAbilityId = normalizeId(abilityId) ?: return 1.0
        val abilityTags = getAbilityTags(normalizedAbilityId)
        val moveTags = getMoveTags(moveId)
        if (abilityTags.isEmpty() || moveTags.isEmpty()) {
            return 1.0
        }

        var multiplier = 1.0
        val appliedMoveTags = mutableSetOf<String>()

        abilityTagMultipliersById[normalizedAbilityId]?.forEach { (moveTag, tagMultiplier) ->
            if (moveTag in moveTags) {
                multiplier *= tagMultiplier
                appliedMoveTags += moveTag
            }
        }

        abilityTags
            .asSequence()
            .filter { it.startsWith("boost_") }
            .map { it.removePrefix("boost_") }
            .filter { it.isNotBlank() && it in moveTags && it !in appliedMoveTags }
            .forEach {
                multiplier *= DEFAULT_BOOST_TAG_MULTIPLIER
                appliedMoveTags += it
            }

        if ("boost" in abilityTags) {
            abilityTags
                .asSequence()
                .filter { it != "boost" && !it.startsWith("boost_") }
                .filter { it in moveTags && it !in appliedMoveTags }
                .forEach {
                    multiplier *= DEFAULT_BOOST_TAG_MULTIPLIER
                    appliedMoveTags += it
                }
        }

        return multiplier
    }

    private fun normalizeId(id: String?): String? = normalizeTag(id)

    private fun normalizeTags(tags: Iterable<String>): Set<String> {
        return tags.mapNotNull(::normalizeTag).toSet()
    }

    private fun normalizeTag(tag: String?): String? {
        val normalizedTag = tag?.trim()?.lowercase()
        return if (normalizedTag.isNullOrBlank()) null else normalizedTag
    }

    private fun loadMoveTags(manager: ResourceManager) {
        moveFolders.forEach { folder ->
            manager.listResources(folder) { it.path.endsWith(".json") }.forEach { (identifier, resource) ->
                val moveId = normalizeId(File(identifier.path).nameWithoutExtension) ?: return@forEach
                resource.open().use { stream ->
                    stream.bufferedReader().use { reader ->
                        try {
                            val entry = gson.fromJson(reader, MoveTagEntry::class.java) ?: MoveTagEntry()
                            val tags = normalizeTags(entry.tags)
                            if (tags.isNotEmpty()) {
                                moveTagsById.computeIfAbsent(moveId) { mutableSetOf() }.addAll(tags)
                            }
                        } catch (exception: JsonSyntaxException) {
                            Cobblemon.LOGGER.error("Failed to parse move battle AI tags for {}", identifier, exception)
                        }
                    }
                }
            }
        }
    }

    private fun loadAbilityTags(manager: ResourceManager) {
        abilityFolders.forEach { folder ->
            manager.listResources(folder) { it.path.endsWith(".json") }.forEach { (identifier, resource) ->
                val abilityId = normalizeId(File(identifier.path).nameWithoutExtension) ?: return@forEach
                resource.open().use { stream ->
                    stream.bufferedReader().use { reader ->
                        try {
                            val entry = gson.fromJson(reader, AbilityTagEntry::class.java) ?: AbilityTagEntry()
                            val tags = normalizeTags(entry.tags)
                            if (tags.isNotEmpty()) {
                                abilityTagsById.computeIfAbsent(abilityId) { mutableSetOf() }.addAll(tags)
                            }
                            val tagMultipliers = entry.tagMultipliers
                                .mapNotNull { (tag, multiplier) ->
                                    val normalizedTag = normalizeTag(tag) ?: return@mapNotNull null
                                    if (multiplier <= 0.0) return@mapNotNull null
                                    normalizedTag to multiplier
                                }
                                .toMap()
                            if (tagMultipliers.isNotEmpty()) {
                                abilityTagMultipliersById
                                    .computeIfAbsent(abilityId) { mutableMapOf() }
                                    .putAll(tagMultipliers)
                            }
                        } catch (exception: JsonSyntaxException) {
                            Cobblemon.LOGGER.error("Failed to parse ability battle AI tags for {}", identifier, exception)
                        }
                    }
                }
            }
        }
    }
}

data class AbilityTagEntry(
    @SerializedName("tags")
    val tags: List<String> = emptyList(),
    @SerializedName("tag_multipliers")
    val tagMultipliers: Map<String, Double> = emptyMap()
)

data class MoveTagEntry(
    @SerializedName("tags")
    val tags: List<String> = emptyList()
)
