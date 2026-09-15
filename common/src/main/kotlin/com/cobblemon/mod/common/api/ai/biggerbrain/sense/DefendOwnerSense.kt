/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities

/**
 * Senses if there is an entity this enties owner needs defending from
 *
 * @author Jack
 * @since April 12th, 2026
 */
class DefendOwnerSense(
    var cooldownTicks: Int = 10
) : Sense {
    override var ticksUntilNextExecution = cooldownTicks
    override val memories = setOf(CobblemonMemories.NEAREST_VISIBLE_ATTACKER, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)

    override fun merge(other: Sense): Boolean {
        if (other is DefendOwnerSense) {
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (entity !is PokemonEntity) return
        val owner = entity.owner ?: return
        brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)?.let { visibleMobs ->
            setNearestAttacker(entity, visibleMobs, owner, brain)
        }
    }

    private fun setNearestAttacker(entity: PokemonEntity, visibleMobs: NearestVisibleLivingEntities, owner: LivingEntity, brain: BiggerBrain) {
        val nearestAttacker = visibleMobs.findClosest { livingEntity ->
            if (livingEntity is ServerPlayer) return@findClosest false
            if (livingEntity.lastHurtMob == owner) return@findClosest true
            if (livingEntity is Mob && livingEntity.target == owner) return@findClosest true
            val mobAttackTarget = livingEntity.brain.getMemorySafely(MemoryModuleType.ATTACK_TARGET)
            if (mobAttackTarget.isPresent && mobAttackTarget.get() == owner) return@findClosest true
            false
        }.orElse(null)

        if (nearestAttacker != null) {
            brain.setMemory(CobblemonMemories.NEAREST_VISIBLE_ATTACKER, nearestAttacker)
        } else {
            brain.eraseMemoryIfPresent(CobblemonMemories.NEAREST_VISIBLE_ATTACKER)
        }
    }

    class Config : SenseConfig {
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("10".asExpression())

        override fun getVariables(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
            listOf(cooldownTicks).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext) =
            DefendOwnerSense(cooldownTicks.resolveInt(behaviourConfigurationContext.runtime))
    }
}