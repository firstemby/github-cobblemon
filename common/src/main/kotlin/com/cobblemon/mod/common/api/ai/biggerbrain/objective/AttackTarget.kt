/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.SimpleMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective.Companion.OBJECTIVE_TAG_WANDERING
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
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player

class AttackTarget(
    private val speedMultiplier: Expression = "1.0".asExpression(),
    private val attackRange: Expression = "2.0".asExpression(),
    private val attackCooldownTicks: Int = 20
) : Objective {
    private var movementTask: EntityFixation? = null
    private var remainingCooldownTicks = 0

    override val observedMemories = listOf(MemoryModuleType.ATTACK_TARGET)

    override fun getDescription(entity: LivingEntity) = "Attack target"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID
        if (entity !is PathfinderMob) return ObjectiveStatus.INVALID

        val target = brain.getMemory(MemoryModuleType.ATTACK_TARGET) as? LivingEntity
            ?: return ObjectiveStatus.INACTIVE

        activeObjectiveTags.add(OBJECTIVE_TAG_WANDERING)

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

        if (entity.distanceToSqr(target) <= range * range && entity.hasLineOfSight(target)) {
            if (remainingCooldownTicks <= 0) {
                if (entity.doHurtTarget(target)) {
                    entity.swing(InteractionHand.MAIN_HAND)
                }
                remainingCooldownTicks = attackCooldownTicks
            }
            return ObjectiveStatus.ACTIVE
        }

        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        if (!brain.distract(Distraction.FORCE)) return ObjectiveStatus.BLOCKED
        val walkTarget = WalkTarget(EntityTracker(target, true), speed, 1)
        val movementTracker = RegularMovementTracker.attempt(entity, walkTarget) ?: return ObjectiveStatus.ACTIVE
        movementTask = brain.fixate(SimpleMoveFixation(movementTracker, "Attack target"))
        return ObjectiveStatus.ACTIVE
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(Priority.NORMAL, MemoryModuleType.ATTACK_TARGET) { value, _ ->
            if (value == null) {
                movementTask = null
            }
            true
        }
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
            return AttackTarget(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                attackRange = attackRange.asSimplifiedExpression(entity),
                attackCooldownTicks = behaviourConfigurationContext.runtime.resolveInt(
                    attackCooldownTicks.asSimplifiedExpression(entity)
                )
            )
        }
    }
}
