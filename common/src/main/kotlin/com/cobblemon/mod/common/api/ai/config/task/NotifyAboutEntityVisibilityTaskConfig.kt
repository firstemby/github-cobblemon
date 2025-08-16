/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.config.task

import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.EntityCallbacks
import com.cobblemon.mod.common.entity.MoLangScriptingEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.BehaviorControl
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.sensing.SensorType

/**
 * Frequently checks a diff on the visibility of entities around the given entity so EntityCallbacks can be triggered.
 *
 * @author Hiroku
 * @since August 7th, 2025
 */
class NotifyAboutEntityVisibilityTaskConfig : SingleTaskConfig {
    val ticksBetweenChecks: ExpressionOrEntityVariable = Either.left("20".asExpression())

    override fun getVariables(entity: LivingEntity) = listOf(ticksBetweenChecks).asVariables()
    override fun createTask(
        entity: LivingEntity,
        behaviourConfigurationContext: BehaviourConfigurationContext
    ): BehaviorControl<in LivingEntity>? {
        if (entity !is MoLangScriptingEntity) {
            return null
        }

        val ticksBetweenChecks = ticksBetweenChecks.resolveInt()

        behaviourConfigurationContext.addMemories(MemoryModuleType.NEAREST_LIVING_ENTITIES)
        behaviourConfigurationContext.addSensors(SensorType.NEAREST_LIVING_ENTITIES)

        return object : Behavior<LivingEntity>(mapOf(MemoryModuleType.NEAREST_LIVING_ENTITIES to MemoryStatus.VALUE_PRESENT), Int.MAX_VALUE) {
            // Buffer of known entities
            var knownEntities = listOf<LivingEntity>()
            override fun checkExtraStartConditions(level: ServerLevel, owner: LivingEntity) = true
            override fun tick(level: ServerLevel, owner: LivingEntity, gameTime: Long) {
                if (gameTime % ticksBetweenChecks != 0L) {
                    return
                }

                val visibleEntities = owner.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                    .orElse(emptyList<LivingEntity>())
                    .filter { it.isAlive }

                val newEntities = visibleEntities.filterNot { it in knownEntities }
                val removedEntities = knownEntities.filterNot { it in visibleEntities }

                newEntities.forEach {
                    entity.callbacks.process(
                        type = EntityCallbacks.SAW_ENTITY,
                        functions = mapOf("other_entity" to { _ -> it.asMostSpecificMoLangValue() })
                    )
                }

                removedEntities.forEach {
                    entity.callbacks.process(
                        type = EntityCallbacks.LOST_ENTITY,
                        functions = mapOf("other_entity" to { _ -> it.asMostSpecificMoLangValue() })
                    )
                }

                knownEntities = visibleEntities.toList()
            }
        }
    }
}