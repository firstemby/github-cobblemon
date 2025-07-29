/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.sendParticlesServer
import com.mojang.datafixers.util.Either
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.phys.Vec3

class PollinateFlowerTaskConfig : SingleTaskConfig {
    companion object {
        const val POLLINATE = "pollinate"
    }

    val condition = booleanVariable(POLLINATE, "can_pollinate", true).asExpressible()
    val durationTicks: ExpressionOrEntityVariable = Either.left("30".asExpression())

    override fun getVariables(entity: LivingEntity) = listOf(
        condition
    ).asVariables()

    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is PokemonEntity) {
            return null
        }
        if (!checkCondition(entity, condition)) {
            return null
        }

        return object : Behavior<LivingEntity>(
            mapOf(
                CobblemonMemories.NEARBY_FLOWERS to MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET to MemoryStatus.VALUE_ABSENT,
                CobblemonMemories.POLLINATED to MemoryStatus.VALUE_ABSENT,
                CobblemonMemories.HIVE_COOLDOWN to MemoryStatus.VALUE_ABSENT,
            ),
            15,
            45
        ) {
            override fun checkExtraStartConditions(level: ServerLevel, owner: LivingEntity): Boolean {
                return entity.brain.getMemorySafely(CobblemonMemories.NEARBY_FLOWERS).orElse(emptyList()).any {
                    Vec3.atCenterOf(it).distanceTo(entity.position()) <= 1.0
                }
            }

            override fun canStillUse(level: ServerLevel, entity: LivingEntity, gameTime: Long): Boolean {
                return checkExtraStartConditions(level, entity)
            }

            override fun start(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
                super.start(level, entity, gameTime)
            }

            override fun tick(level: ServerLevel, owner: LivingEntity, gameTime: Long) {
                level.sendParticlesServer(
                    particleType = ParticleTypes.DRIPPING_HONEY,
                    position = owner.eyePosition,
                    particles = 3,
                    offset = Vec3.ZERO,
                    speed = 0.0
                )
            }

            override fun stop(level: ServerLevel, entity: LivingEntity, gameTime: Long) {
                val flowerStillThere = canStillUse(level, entity, gameTime)
                if (flowerStillThere) {
                    entity.brain.setMemory(CobblemonMemories.POLLINATED, true)
                }
            }
        }
    }
}