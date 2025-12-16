/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.api.battles.ai.config.PredictionConfig
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Global registry for battle memory instances.
 * This allows battle instructions to record events that the AI can later reference.
 *
 * The registry uses concurrent maps to support thread-safe access from
 * both the battle interpreter and AI decision-making code.
 *
 * @author Amo Aster
 * @since December 2025
 */
object BattleMemoryRegistry {

    /** Maps battle ID to its memory instance */
    private val memories = ConcurrentHashMap<UUID, AIBattleMemory>()

    /** Maps battle ID to prediction config (set by AI when it initializes) */
    private val configs = ConcurrentHashMap<UUID, PredictionConfig>()

    /**
     * Gets or creates memory for a battle.
     * If a config has been set for this battle, it will be used.
     * Otherwise, the default config is used.
     *
     * @param battleId The UUID of the battle
     * @return The AIBattleMemory for this battle
     */
    fun getOrCreate(battleId: UUID): AIBattleMemory {
        return memories.computeIfAbsent(battleId) {
            val config = configs[battleId] ?: PredictionConfig.DEFAULT
            AIBattleMemory(battleId, config)
        }
    }

    /**
     * Gets memory for a battle if it exists.
     *
     * @param battleId The UUID of the battle
     * @return The AIBattleMemory if it exists, null otherwise
     */
    fun get(battleId: UUID): AIBattleMemory? {
        return memories[battleId]
    }

    /**
     * Sets the prediction config for a battle.
     * Should be called by the AI before memory is first accessed.
     *
     * @param battleId The UUID of the battle
     * @param config The prediction configuration
     */
    fun setConfig(battleId: UUID, config: PredictionConfig) {
        configs[battleId] = config
        // If memory already exists, we can't change its config
        // but that's okay, configs should be set before first access
    }

    /**
     * Removes memory for a battle (cleanup when battle ends).
     *
     * @param battleId The UUID of the battle
     */
    fun remove(battleId: UUID) {
        memories.remove(battleId)
        configs.remove(battleId)
    }

    /**
     * Clears all battle memories.
     * Mainly useful for testing.
     */
    fun clear() {
        memories.clear()
        configs.clear()
    }

    /**
     * Gets the number of active battle memories.
     *
     * @return The count of active memories
     */
    fun size(): Int = memories.size
}
