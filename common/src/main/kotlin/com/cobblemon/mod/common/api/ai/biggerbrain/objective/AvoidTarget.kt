/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.entity.player.Player

class AvoidTarget(
    private val speedMultiplier: Expression = "0.5".asExpression(),
) : Objective {
    private var movementTask: EntityFixation? = null
    private var remainingCooldownTicks = 0

    override val observedMemories = listOf(MemoryModuleType.AVOID_TARGET)

    override fun getDescription(entity: LivingEntity) = "Avoiding target"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val target = brain.getMemory(MemoryModuleType.AVOID_TARGET)
            ?: return ObjectiveStatus.INACTIVE

        if (target.isRemoved || target.isDeadOrDying) {
            brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
            return ObjectiveStatus.INACTIVE
        }

        if (target is Player && (target.isCreative || target.isSpectator)) {
            brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
            return ObjectiveStatus.INACTIVE
        }

        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }

        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val speed = runtime.resolveFloat(speedMultiplier)


        // If we have a current target, check if it's still valid and within roam range (if tethered)
        if (target.isAlive) {
            val tethering = (entity).tethering
            if (tethering != null && !tethering.canRoamTo(target.blockPosition())) {
                // Target moved out of range let's kill the memory >:)
                brain.distract(Distraction.FORCE)
                entity.brain.eraseMemory(MemoryModuleType.AVOID_TARGET)
            }
        }

        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        if (!brain.distract(Distraction.FORCE)) return ObjectiveStatus.BLOCKED
        var targetPos : BlockPos? = null

        // TODO: utilize wander target rules to find a valid target position
        for (j in 0..9) {
            val direction = entity.position().subtract(target.position())
            val targetVec3 = LandRandomPos.getPosAway(entity, 16, 7, direction)
            if (targetVec3 != null) {
                targetPos = BlockPos.containing(targetVec3)
                break
            }
        }
//        val walkTarget = WanderAround.generateWalkTarget(entity)
        if (targetPos != null) {
            val movementTracker = RegularMovementTracker.attempt(
                entity = entity,
                walkTarget = WalkTarget(
                    targetPos,
                    speed,
                    0
                )
            )
            if (movementTracker != null) {
                movementTask = entity.biggerBrain.fixate(
                    LookAndMoveFixation(
                        distractionTypes = setOf(
                            DistractionType.FORCE
                        ),
                        lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(
                            entity,
                            movementTracker.walkTarget
                        ),
                        movementTracker = movementTracker,
                        description = "Moving away from target entity",
                    )
                )
                return ObjectiveStatus.FIXATED
            }
        }



        return ObjectiveStatus.ACTIVE
    }

    override fun <T> onMemoryChanged(
        entity: LivingEntity,
        brain: BiggerBrain,
        memory: MemoryModuleType<T>,
        value: T?
    ) {
        if (memory == MemoryModuleType.AVOID_TARGET) {
            if (value == null) {
                if (brain.isFixation(movementTask)) {
                    brain.distract(Distraction.FORCE)
                    movementTask = null
                }
            }
        }
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(speedMultiplier).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return AvoidTarget(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
            )
        }
    }
}
