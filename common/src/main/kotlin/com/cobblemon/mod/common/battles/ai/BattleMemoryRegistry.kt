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
 * Global registry for battle memory instances, allowing battle instructions
 * to record events that the AI can later reference.
 */
object BattleMemoryRegistry {

    private val memories = ConcurrentHashMap<UUID, AIBattleMemory>()
    private val configs = ConcurrentHashMap<UUID, PredictionConfig>()

    fun getOrCreate(battleId: UUID): AIBattleMemory {
        return memories.computeIfAbsent(battleId) {
            val config = configs[battleId] ?: PredictionConfig.DEFAULT
            AIBattleMemory(battleId, config)
        }
    }

    fun get(battleId: UUID): AIBattleMemory? {
        return memories[battleId]
    }

    fun setConfig(battleId: UUID, config: PredictionConfig) {
        configs[battleId] = config
    }

    fun remove(battleId: UUID) {
        memories.remove(battleId)
        configs.remove(battleId)
    }

    fun clear() {
        memories.clear()
        configs.clear()
    }

    fun size(): Int = memories.size
}
