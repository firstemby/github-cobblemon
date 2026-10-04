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
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
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
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.movement.MovementIntent
import com.cobblemon.mod.common.api.ai.movement.TraversalPolicy
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player

class AttackTargetMelee(
    private val speedMultiplier: Expression = "1.0".asExpression(),
    private val attackRange: Expression = "2.0".asExpression(),
    private val attackCooldownTicks: Int = 20
) : Objective {
    private var movementTask: EntityFixation? = null
    private var movementTracker: MovementTracker? = null
    private var remainingCooldownTicks = 0
    /** Ticks until the chase may plan again after failing, so an unreachable target doesn't get re-pathed every tick. */
    private var remainingRepathTicks = 0

    override val observedMemories = listOf(MemoryModuleType.ATTACK_TARGET)

    override fun getDescription(entity: LivingEntity) = "Attack target melee"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val target = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
            ?: return ObjectiveStatus.INACTIVE

        if (target.isRemoved || target.isDeadOrDying) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return ObjectiveStatus.INACTIVE
        }

        if (target is Player && (target.isCreative || target.isSpectator)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return ObjectiveStatus.INACTIVE
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }

        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val range = runtime.resolveFloat(attackRange)
        val speed = runtime.resolveFloat(speedMultiplier)

        // This is melee thing, but not everything will do melee in the future
        if (entity.distanceToSqr(target) <= range * range && entity.hasLineOfSight(target)) {
            if (remainingCooldownTicks <= 0) {
                if (entity.doHurtTarget(target)) {
                    entity.swing(InteractionHand.MAIN_HAND)
                    var attackTargetData = brain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                    if (attackTargetData != null) {
                        attackTargetData.hitsLanded += 1

                    } else {
                        attackTargetData = CobblemonAttackTargetData()
                        attackTargetData.hitsLanded = 1
                    }
                    brain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
                }
                remainingCooldownTicks = attackCooldownTicks
            }
            return ObjectiveStatus.ACTIVE
        }

        // If we have a current target, check if it's still valid and within roam range (if tethered)
        if (target.isAlive) {
            val tethering = (entity).tethering
            if (tethering != null && !tethering.canRoamTo(target.blockPosition())) {
                // Target moved out of range let's kill the memory >:)
                brain.distract(Distraction.FORCE)
                entity.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            }
        }

        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        if (movementTracker?.lastStatus == MovementStatus.FAILED) {
            // Got as close as it's willing to (e.g. the bank of a lake it won't enter). Hold there for a bit.
            movementTracker = null
            remainingRepathTicks = REPATH_COOLDOWN_TICKS
        }
        if (remainingRepathTicks > 0) {
            remainingRepathTicks--
            entity.lookControl.setLookAt(target)
            return ObjectiveStatus.ACTIVE
        }

        if (!brain.distract(Distraction.FORCE)) return ObjectiveStatus.BLOCKED
        val walkTarget = WalkTarget(EntityTracker(target, true), speed, 1)
        val policy = TraversalPolicy.forIntent(entity, MovementIntent.URGENT)
        val movementTracker = RegularMovementTracker.attempt(entity, walkTarget, policy)
        this.movementTracker = movementTracker
        if (movementTracker == null) {
            remainingRepathTicks = REPATH_COOLDOWN_TICKS
            return ObjectiveStatus.ACTIVE
        }
        movementTask = brain.fixate(LookAndMoveFixation(
            distractionTypes = setOf(DistractionType.FORCE),
            lookingTracker = LookingTracker.forTargetEntity(entity, target, trackEyeHeight = true),
            movementTracker = movementTracker,
            description = "Moving to attack target"))
        return ObjectiveStatus.FIXATED
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(
            priority = Priority.LOW,
            memory = MemoryModuleType.ATTACK_TARGET
        ) { value, labels ->
            if (value == null) {
                if (brain.isFixation(movementTask)) {
                    brain.distract(Distraction.FORCE)
                    movementTask = null
                }
            }
            return@subscribe true
        }
    }

    companion object {
        const val REPATH_COOLDOWN_TICKS = 20
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("1.0".asExpression())
        val attackRange: ExpressionOrEntityVariable = Either.left("2.0".asExpression())
        val attackCooldownTicks: ExpressionOrEntityVariable = Either.left("20".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(speedMultiplier, attackRange, attackCooldownTicks).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return AttackTargetMelee(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                attackRange = attackRange.asSimplifiedExpression(entity),
                attackCooldownTicks = behaviourConfigurationContext.runtime.resolveInt(
                    attackCooldownTicks.asSimplifiedExpression(entity)
                )
            )
        }
    }
}
