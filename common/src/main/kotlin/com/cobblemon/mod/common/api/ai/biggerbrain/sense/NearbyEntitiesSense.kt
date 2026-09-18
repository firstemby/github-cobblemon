/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.sense

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.config.sense.SenseConfig
import com.cobblemon.mod.common.api.ai.resolveDouble
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.datafixers.util.Either
import java.util.function.Predicate
import java.util.function.ToDoubleFunction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities

/**
 * Senses nearby entities. Maintains the NEAREST_LIVING_ENTITIES and NEAREST_VISIBLE_LIVING_ENTITIES memories.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class NearbyEntitiesSense(
    var condition: Expression,
    var radiusXZ: Double = 16.0,
    var radiusY: Double = 16.0,
    var cooldownTicks: Int = 20 * 5
) : Sense {
    override var ticksUntilNextExecution: Int = 50
    override val memories = setOf(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)

    override fun merge(other: Sense): Boolean {
        if (other is NearbyEntitiesSense) {
            this.condition = other.condition
            this.cooldownTicks = minOf(this.cooldownTicks, other.cooldownTicks)
            this.radiusXZ = other.radiusXZ
            this.radiusY = other.radiusY
            return true
        } else {
            return false
        }
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain) {
        ticksUntilNextExecution = cooldownTicks
        if (!brain.runtime.resolveBoolean(condition)) {
            return
        }
        val level = entity.level()
        val aABB = entity.boundingBox.inflate(radiusXZ, radiusY, radiusXZ)
        val list = level.getEntitiesOfClass(LivingEntity::class.java, aABB, Predicate { it !== entity && it!!.isAlive })
        list.sortWith(Comparator.comparingDouble(ToDoubleFunction { it.distanceToSqr(entity) }))
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, list)
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, NearestVisibleLivingEntities(entity, list))
    }

    class Config : SenseConfig {
        var condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var radiusXZ: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var radiusY: ExpressionOrEntityVariable = Either.left("16.0".asExpression())
        var cooldownTicks: ExpressionOrEntityVariable = Either.left("60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            condition,
            radiusXZ,
            radiusY,
            cooldownTicks
        ).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Sense {
            return NearbyEntitiesSense(
                condition = condition.asSimplifiedExpression(entity),
                radiusXZ = radiusXZ.resolveDouble(behaviourConfigurationContext.runtime),
                radiusY = radiusY.resolveDouble(behaviourConfigurationContext.runtime),
                cooldownTicks = cooldownTicks.resolveDouble(behaviourConfigurationContext.runtime).toInt()
            )
        }
    }
}