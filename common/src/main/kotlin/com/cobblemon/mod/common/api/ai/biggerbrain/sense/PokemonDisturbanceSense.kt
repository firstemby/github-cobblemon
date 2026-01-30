/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class PokemonDisturbanceSense(
    private var cooldownTicks: Int = 20
) : Sense {
    override var ticksUntilNextExecution: Int = 20
    override val memories = setOf(MemoryModuleType.DISTURBANCE_LOCATION)

    override fun merge(other: Sense): Boolean {
        if (other is PokemonDisturbanceSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        }
        return false
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        val world = entity.level() as? ServerLevel ?: return

        val nearestPlayer = world.players()
            .filter { it.isAlive && entity.distanceToSqr(it) <= 16 * 16 }
            .minByOrNull { entity.distanceToSqr(it) }

        nearestPlayer?.let {
            brain.setMemory(MemoryModuleType.DISTURBANCE_LOCATION, BlockPos(it.blockPosition()))
        }
    }

    class Config : SenseConfig {
        override fun create(entity: LivingEntity, behaviourConfigurationContext: com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext): Sense? {
            return PokemonDisturbanceSense()
        }
    }
}
