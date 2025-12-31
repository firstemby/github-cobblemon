/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Maintains memory of the last entity that hurt this entity.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class HurtBySense(
    var cooldownTicks: Int = 2
) : Sense {
    override var ticksUntilNextExecution: Int = 1
    override val memories = setOf(MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY)

    override fun merge(other: Sense): Boolean {
        if (other is HurtBySense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        val damageSource = entity.lastDamageSource
        if (damageSource != null) {
            brain.setMemoryIfChanged(MemoryModuleType.HURT_BY, entity.lastDamageSource)
            val attacker = damageSource.entity
            if (attacker is LivingEntity) {
                brain.setMemoryIfChanged(MemoryModuleType.HURT_BY_ENTITY, attacker)
            }
        } else {
            brain.eraseMemoryIfPresent(MemoryModuleType.HURT_BY)
        }

        // so apparently it remembers the entity that hurt them even if it was forever ago? fair enough I guess.
        brain.getMemory(MemoryModuleType.HURT_BY_ENTITY)?.let { livingEntity ->
            if (!livingEntity.isAlive || livingEntity.level() != entity.level()) {
                brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY)
            }
        }

        ticksUntilNextExecution = cooldownTicks
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("2".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            val cooldownTicks = cooldownTicks.resolveInt(behaviourConfigurationContext.runtime)
            return HurtBySense(cooldownTicks = cooldownTicks)
        }
    }
}