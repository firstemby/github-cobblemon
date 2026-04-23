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
import com.cobblemon.mod.common.util.endsWith
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager
import java.io.File

/**
 * Registry for Battle AI configurations loaded from datapacks.
 * Files are loaded from `data/<namespace>/battle_ai/`.
 * For backwards compatibility, also loads from `data/<namespace>/battle_ai_configs/` with a deprecation warning.
 *
 * Supports configuration inheritance - configs can specify a "parent" field
 * to inherit values from another config.
 */
object BattleAIConfigs : JsonDataRegistry<BattleAIConfig> {
    override val id = cobblemonResource("battle_ai")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BattleAIConfigs>()
    override val typeToken: TypeToken<BattleAIConfig> = TypeToken.get(BattleAIConfig::class.java)
    override val resourcePath = "battle_ai"

    private const val DEPRECATED_RESOURCE_PATH = "battle_ai_configs"
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

    val RANDOM = cobblemonResource("random")
    val EASY = cobblemonResource("easy")
    val MEDIUM = cobblemonResource("medium")
    val HARD = cobblemonResource("hard")
    val EXPERT = cobblemonResource("expert")
    val GEN6_STEVEN = cobblemonResource("gen6_steven")
    val GEN7_KUKUI = cobblemonResource("gen7_kukui")
    val GEN8_LEON = cobblemonResource("gen8_leon")
    val GEN9_NEMONA = cobblemonResource("gen9_nemona")

    @Volatile private var rawConfigs = mapOf<ResourceLocation, BattleAIConfig>()
    @Volatile private var resolvedConfigs = mapOf<ResourceLocation, BattleAIConfig>()

    override fun sync(player: ServerPlayer) {}

    override fun reload(manager: ResourceManager) {
        val data = hashMapOf<ResourceLocation, BattleAIConfig>()

        // Load from deprecated path first (lower priority)
        manager.listResources(DEPRECATED_RESOURCE_PATH) { path -> path.endsWith(JsonDataRegistry.JSON_EXTENSION) }
            .forEach { (identifier, resource) ->
                resource.open().use { stream ->
                    stream.bufferedReader().use { reader ->
                        val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(
                            identifier.namespace,
                            File(identifier.path).nameWithoutExtension
                        )
                        Cobblemon.LOGGER.warn(
                            "Battle AI config '{}' loaded from deprecated path '{}'. " +
                            "Move it to 'data/{}/battle_ai/' instead.",
                            resolvedIdentifier, DEPRECATED_RESOURCE_PATH, identifier.namespace
                        )
                        data[resolvedIdentifier] = parse(reader, resolvedIdentifier)
                    }
                }
            }

        // Load from new path (higher priority — overwrites deprecated entries)
        manager.listResources(resourcePath) { path -> path.endsWith(JsonDataRegistry.JSON_EXTENSION) }
            .forEach { (identifier, resource) ->
                resource.open().use { stream ->
                    stream.bufferedReader().use { reader ->
                        val resolvedIdentifier = ResourceLocation.fromNamespaceAndPath(
                            identifier.namespace,
                            File(identifier.path).nameWithoutExtension
                        )
                        data[resolvedIdentifier] = parse(reader, resolvedIdentifier)
                    }
                }
            }

        reload(data)
    }

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

    fun getConfig(id: ResourceLocation): BattleAIConfig? {
        return resolvedConfigs[id]
    }

    fun getOrDefault(id: ResourceLocation?): BattleAIConfig {
        if (id == null) return BattleAIConfig.DEFAULT
        return resolvedConfigs[id] ?: BattleAIConfig.DEFAULT
    }

    fun getAllIds(): Set<ResourceLocation> = resolvedConfigs.keys.toSet()

    fun getAll(): Collection<BattleAIConfig> = resolvedConfigs.values

    fun exists(id: ResourceLocation): Boolean = id in resolvedConfigs

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

    fun getBySkillLevel(skill: Int): BattleAIConfig {
        return getOrDefault(fromSkillLevel(skill))
    }
}
