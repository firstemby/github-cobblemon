/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.LowercaseEnumAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

/**
 * Registry for Battle AI configurations loaded from datapacks.
 * Files are loaded from `data/<namespace>/battle_ai_configs/`.
 *
 * Supports configuration inheritance - configs can specify a "parent" field
 * to inherit values from another config.
 *
 * Built-in presets:
 * - `cobblemon:random` - Pure random move selection
 * - `cobblemon:easy` - Skill level 1-2, basic decisions
 * - `cobblemon:medium` - Skill level 3, some strategy
 * - `cobblemon:hard` - Skill level 4, good strategy
 * - `cobblemon:expert` - Skill level 5, optimal play
 *
 * @author Amo Aster
 * @since December 2025
 */
object BattleAIConfigs : JsonDataRegistry<BattleAIConfig> {
    override val id = cobblemonResource("battle_ai_configs")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BattleAIConfigs>()
    override val typeToken: TypeToken<BattleAIConfig> = TypeToken.get(BattleAIConfig::class.java)
    override val resourcePath = "battle_ai_configs"
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(MoveCategory::class.java, LowercaseEnumAdapter(MoveCategory::class.java))
        .registerTypeAdapter(ActionType::class.java, LowercaseEnumAdapter(ActionType::class.java))
        .registerTypeAdapter(ModifierType::class.java, LowercaseEnumAdapter(ModifierType::class.java))
        .registerTypeAdapter(GimmickType::class.java, LowercaseEnumAdapter(GimmickType::class.java))
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    // Built-in preset identifiers
    val RANDOM = cobblemonResource("random")
    val EASY = cobblemonResource("easy")
    val MEDIUM = cobblemonResource("medium")
    val HARD = cobblemonResource("hard")
    val EXPERT = cobblemonResource("expert")

    /** Raw configs before inheritance resolution */
    private val rawConfigs = mutableMapOf<ResourceLocation, BattleAIConfig>()

    /** Resolved configs with inheritance applied */
    private val resolvedConfigs = mutableMapOf<ResourceLocation, BattleAIConfig>()

    /** Tracks configs currently being resolved to detect circular inheritance */
    private val resolvingStack = mutableSetOf<ResourceLocation>()

    /** Server-side only, no sync needed */
    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, BattleAIConfig>) {
        rawConfigs.clear()
        resolvedConfigs.clear()

        // Store raw configs with their IDs
        data.forEach { (identifier, config) ->
            rawConfigs[identifier] = config.copy(id = identifier)
        }

        // Resolve all configs with inheritance
        rawConfigs.keys.forEach { id ->
            try {
                resolveConfig(id)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("Failed to resolve battle AI config '$id': ${e.message}")
            }
        }

        Cobblemon.LOGGER.info("Loaded ${resolvedConfigs.size} battle AI configs")
        observable.emit(this)
    }

    /**
     * Resolves a config by ID, applying inheritance from parent configs.
     * Uses caching to avoid re-resolving configs.
     *
     * @param id The config identifier to resolve
     * @return The resolved config with inheritance applied
     * @throws IllegalStateException if circular inheritance is detected
     */
    private fun resolveConfig(id: ResourceLocation): BattleAIConfig {
        resolvedConfigs[id]?.let { return it }

        if (id in resolvingStack) {
            throw IllegalStateException("Circular inheritance detected for config '$id'. Chain: ${resolvingStack.joinToString(" -> ")} -> $id")
        }

        val rawConfig = rawConfigs[id] ?: return BattleAIConfig.DEFAULT

        if (rawConfig.parent == null) {
            resolvedConfigs[id] = rawConfig
            return rawConfig
        }

        // Resolve parent first
        resolvingStack.add(id)
        try {
            val parentConfig = resolveConfig(rawConfig.parent)
            val resolved = rawConfig.resolveWithParent(parentConfig)
            resolvedConfigs[id] = resolved
            return resolved
        } finally {
            resolvingStack.remove(id)
        }
    }

    /**
     * Gets a config by its identifier.
     *
     * @param id The config identifier
     * @return The resolved config, or null if not found
     */
    fun getConfig(id: ResourceLocation): BattleAIConfig? {
        return resolvedConfigs[id]
    }

    /**
     * Gets a config by its identifier, falling back to the default config if not found.
     *
     * @param id The config identifier, or null to get the default
     * @return The resolved config, or the default config
     */
    fun getOrDefault(id: ResourceLocation?): BattleAIConfig {
        if (id == null) return BattleAIConfig.DEFAULT
        return resolvedConfigs[id] ?: BattleAIConfig.DEFAULT
    }

    /**
     * Gets all registered config identifiers.
     *
     * @return Set of all config identifiers
     */
    fun getAllIds(): Set<ResourceLocation> = resolvedConfigs.keys.toSet()

    /**
     * Gets all resolved configs.
     *
     * @return Collection of all resolved configs
     */
    fun getAll(): Collection<BattleAIConfig> = resolvedConfigs.values

    /**
     * Checks if a config with the given identifier exists.
     *
     * @param id The config identifier to check
     * @return True if the config exists
     */
    fun exists(id: ResourceLocation): Boolean = id in resolvedConfigs

    /**
     * Maps a legacy skill level (0-5) to a config identifier.
     * This provides backwards compatibility with the old StrongBattleAI skill parameter.
     *
     * @param skill The skill level (0-5)
     * @return The corresponding config identifier
     */
    fun fromSkillLevel(skill: Int): ResourceLocation {
        return when (skill.coerceIn(0, 5)) {
            0 -> RANDOM
            1, 2 -> EASY
            3 -> MEDIUM
            4 -> HARD
            5 -> EXPERT
            else -> EXPERT
        }
    }

    /**
     * Gets a config based on legacy skill level.
     *
     * @param skill The skill level (0-5)
     * @return The resolved config for that skill level
     */
    fun getBySkillLevel(skill: Int): BattleAIConfig {
        return getOrDefault(fromSkillLevel(skill))
    }
}
