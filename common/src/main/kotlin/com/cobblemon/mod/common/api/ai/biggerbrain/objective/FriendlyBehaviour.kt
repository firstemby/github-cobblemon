/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.*
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.comfortDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.*
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities
import net.minecraft.world.entity.ai.memory.WalkTarget


class FriendlyBehaviour(
    speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
     engageRange: Expression = "10.0".asExpression(),
    disengageRange: Expression = "20.0".asExpression(),
    condition: Expression = "true".asExpression(),
    targetCondition: Expression = "false".asExpression(),
    private val maxDuration: Expression = "300".asExpression(),
    private val description: Expression = "Friendly Behaviour".asExpression(),
) : EntityResponseObjective(
    speedMultiplier = speedMultiplier, // How do we transfer speed to the attack behaviour? Do we want to?
    engageRange = engageRange,
    disengageRange = disengageRange,
    condition = condition, // Self/environment condition check
    targetCondition = targetCondition, // target condition (will be checked on entity interaction)
    description = description,
) {
    private var remainingCooldownTicks = 0
    private var fixation: EntityFixation? = null
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY)
    private val closeEnoughDistance = 1.5
    private var remainingTicks = 0


    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return if (brain.isFixation(fixation)) ObjectiveStatus.FIXATED else ObjectiveStatus.INACTIVE
        } else {
            remainingCooldownTicks = 5
        }

        if (brain.isFixation(fixation)) {
            if (remainingTicks > 0) {
                remainingTicks--
            } else {
                // Got bored, move on
                brain.distract(Distraction.FORCE)
                trackedEntity = null
                fixation = null
            }
            if (trackedEntity != null) {
                // Check if we continue
                return attemptFixation(entity, brain)

            } else {
                trackedEntity = null
                fixation = null
                brain.distract(comfortDistraction)
            }
            return ObjectiveStatus.FIXATED
        } else {
            // Scan visible livingEntities
            val targetEntity = scanForValidTargets(entity, brain)

            if (targetEntity != null) {
                trackedEntity = targetEntity
                val status = attemptFixation(entity, brain)
                if (status == ObjectiveStatus.FIXATED) {
                    remainingTicks = mainThreadRuntime.resolveInt(maxDuration)
                }
                return status
            }

            return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
        }
    }

    fun attemptFixation(entity: PathfinderMob, brain: BiggerBrain) : ObjectiveStatus {
        if (trackedEntity != null) {
            // Check if we continue
            val runtime = mainThreadRuntime.withQueryValue("entity", (entity).asMostSpecificMoLangValue())
                .withQueryValue("target", trackedEntity!!.asMostSpecificMoLangValue())
            val distance = entity.distanceTo(trackedEntity!!)
            if (runtime.resolveFloat(disengageRange) < distance || !shouldContinue(entity, brain)) {
                brain.distract(comfortDistraction)
                trackedEntity = null
                fixation = null
                return ObjectiveStatus.INACTIVE
            } else if (distance <= closeEnoughDistance && !(brain.isFixation(fixation) && fixation is IdleLookFixation)) {
                // transition to look
                val distraction = if(brain.isFixation(fixation)) Distraction.FORCE else idleBoredomDistraction
                if (brain.distract(distraction)) {
                    this.fixation = brain.fixate(
                        IdleLookFixation(
                            LookingTracker(
                                entity,
                                EntityTracker(trackedEntity!!, true)
                            ),
                            remainingTicks
                        )
                    )
                    return ObjectiveStatus.FIXATED
                } else {
                    // could not distract
                    return ObjectiveStatus.BLOCKED
                }
            } else if (distance > closeEnoughDistance && !(brain.isFixation(fixation) && fixation is LookAndMoveFixation)) {
                // transition to walk
                val distraction = if(brain.isFixation(fixation)) Distraction.FORCE else idleBoredomDistraction
                if (brain.distract(distraction)) {
                    val walkTarget =
                        WalkTarget(EntityTracker(trackedEntity!!, true), runtime.resolveFloat(speedMultiplier), 0)
                    val movementTracker = RegularMovementTracker.attempt(entity, walkTarget)
                        ?: return ObjectiveStatus.INACTIVE
                    this.fixation = brain.fixate(
                        LookAndMoveFixation(
                            description = "Following targeted entity",
                            distractionTypes = setOf(
                                DistractionType.COMFORT,
                                DistractionType.DAMAGE,
                                DistractionType.FORCE
                            ),
                            lookingTracker = LookingTracker.forTargetEntity(entity, trackedEntity!!),
                            movementTracker = movementTracker,
                            onMovementComplete = { _, _ -> attemptFixation(entity, brain) } // maintains fixation between moving and looking
                        )
                    )
                    return ObjectiveStatus.FIXATED
                } else {
                    return ObjectiveStatus.BLOCKED
                }

            } else {
                return ObjectiveStatus.FIXATED
            }
        }
        return ObjectiveStatus.INACTIVE
    }

    class IdleLookFixation(
        override val lookingTracker: LookingTracker?,
        var remainingTicks: Int,
    ) : EntityFixation, LookingTrackerFixation {


        override fun getDescription(entity: LivingEntity): String {
            return "Looking at a nearby entity ($remainingTicks ticks left)"
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            return !distraction.isIdleBoredom
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            val targetEntity = lookingTracker?.entity ?: return false
            if ((targetEntity.isRemoved) || entity !is PathfinderMob) {
                return false
            }
            val lookingTracker = lookingTracker
            remainingTicks--
            lookingTracker.tick()
            return remainingTicks > 0
        }
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(
            priority = Priority.LOW,
            memory = CobblemonMemories.ATTACK_TARGET_DATA
        ) { value, labels ->
            if (value != null) {
                val livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET) ?: return@subscribe true
                if (trackedEntity?.uuid == livingEntity.uuid) {
                    // We smacked them, refresh our attack timer
                    val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
                    val attackDuration = runtime.resolveInt(maxDuration)
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity, attackDuration.toLong())
                }
            }
            return@subscribe true
        }
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("20.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val maxDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("Test".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                speedMultiplier,
                engageRange,
                disengageRange,
                maxDuration,
                description,
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return FriendlyBehaviour(
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = targetCondition.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                maxDuration = maxDuration.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity)
            )
        }
    }
}
