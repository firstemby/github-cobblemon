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
    @Volatile private var rawConfigs = mapOf<ResourceLocation, BattleAIConfig>()

    /** Resolved configs with inheritance applied */
    @Volatile private var resolvedConfigs = mapOf<ResourceLocation, BattleAIConfig>()

    /** Server-side only, no sync needed */
    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, BattleAIConfig>) {
        val newRaw = mutableMapOf<ResourceLocation, BattleAIConfig>()
        val newResolved = mutableMapOf<ResourceLocation, BattleAIConfig>()
        val resolvingStack = mutableSetOf<ResourceLocation>()

        data.forEach { (identifier, config) ->
            newRaw[identifier] = config.copy(id = identifier)
        }

        fun resolve(id: ResourceLocation): BattleAIConfig {
            newResolved[id]?.let { return it }
            if (id in resolvingStack) {
                throw IllegalStateException("Circular inheritance detected for config '$id'. Chain: ${resolvingStack.joinToString(" -> ")} -> $id")
            }
            val rawConfig = newRaw[id] ?: return BattleAIConfig.DEFAULT
            if (rawConfig.parent == null) {
                newResolved[id] = rawConfig
                return rawConfig
            }
            resolvingStack.add(id)
            try {
                if (rawConfig.parent !in newRaw) {
                    Cobblemon.LOGGER.warn("Battle AI config '$id' references parent '${rawConfig.parent}' which does not exist. Using defaults for missing parent fields.")
                }
                val parentConfig = resolve(rawConfig.parent)
                val resolved = rawConfig.resolveWithParent(parentConfig)
                newResolved[id] = resolved
                return resolved
            } finally {
                resolvingStack.remove(id)
            }
        }

        newRaw.keys.forEach { id ->
            try {
                resolve(id)
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("Failed to resolve battle AI config '$id': ${e.message}")
            }
        }

        // Atomic swap — active battles will read old or new, never partial
        rawConfigs = newRaw
        resolvedConfigs = newResolved

        Cobblemon.LOGGER.info("Loaded ${newResolved.size} battle AI configs")
        observable.emit(this)
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
