/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

/**
 * Registry for battle bag configurations.
 * Bag configurations define what items NPCs/trainers have available during battle.
 *
 * Files are loaded from: data/<namespace>/battle_bags/<id>.json
 *
 * @author Amo Aster
 * @since December 2025
 */
object BattleBagConfigs : JsonDataRegistry<BattleBagConfig> {

    override val id = cobblemonResource("battle_bags")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<BattleBagConfigs>()

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapter(com.cobblemon.mod.common.api.molang.ExpressionLike::class.java, ExpressionLikeAdapter)
        .create()

    override val typeToken: TypeToken<BattleBagConfig> = TypeToken.get(BattleBagConfig::class.java)
    override val resourcePath = "battle_bags"

    private val configs = mutableMapOf<ResourceLocation, BattleBagConfig>()

    // Predefined bag configuration IDs
    val EMPTY = cobblemonResource("empty")
    val DEFAULT = cobblemonResource("default")
    val TRAINER_BASIC = cobblemonResource("trainer_basic")
    val TRAINER_ADVANCED = cobblemonResource("trainer_advanced")
    val GYM_LEADER = cobblemonResource("gym_leader")
    val ELITE = cobblemonResource("elite")

    override fun reload(data: Map<ResourceLocation, BattleBagConfig>) {
        configs.clear()
        configs.putAll(data)

        if (!configs.containsKey(EMPTY)) {
            configs[EMPTY] = BattleBagConfig.EMPTY
        }
        if (!configs.containsKey(DEFAULT)) {
            configs[DEFAULT] = BattleBagConfig.DEFAULT
        }

        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        // Bag configs don't need to sync to client
    }

    /**
     * Gets a battle bag config by ID.
     * Returns the empty config if not found.
     */
    fun getConfig(id: ResourceLocation): BattleBagConfig {
        return configs[id] ?: BattleBagConfig.EMPTY
    }

    /**
     * Gets a battle bag config by ID, or null if not found.
     */
    fun getConfigOrNull(id: ResourceLocation): BattleBagConfig? {
        return configs[id]
    }

    /**
     * Gets all registered bag configurations.
     */
    fun getAllConfigs(): Map<ResourceLocation, BattleBagConfig> {
        return configs.toMap()
    }

    /**
     * Checks if a config exists.
     */
    fun hasConfig(id: ResourceLocation): Boolean {
        return configs.containsKey(id)
    }

    /**
     * Creates a BattleBagState for tracking item usage during a battle.
     */
    fun createState(id: ResourceLocation): BattleBagState {
        return BattleBagState(getConfig(id))
    }
}
