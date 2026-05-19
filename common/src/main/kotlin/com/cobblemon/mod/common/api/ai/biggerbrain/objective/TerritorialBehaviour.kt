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
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.AttackFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndLockFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.resolveString
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import com.sun.jna.Memory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

class TerritorialBehaviour(
    speedMultiplier: Expression = "0.5".asExpression(), // How do we transfer speed to the attack behaviour? Do we want to?
    val attackRange: Expression = "3.0".asExpression(),
    engageRange: Expression = "10.0".asExpression(),
    disengageRange: Expression = "15.0".asExpression(),
    condition: Expression = "true".asExpression(), // Self/environment condition check
    targetCondition: Expression = "true".asExpression(), // target condition (will be checked on entity interaction)
    description: Expression = "'Territorial Behaviour'".asExpression(),
    val maxAttackDuration: Expression = "300".asExpression(),
) : EntityResponseObjective(
    speedMultiplier = speedMultiplier, // How do we transfer speed to the attack behaviour? Do we want to?
    engageRange = engageRange,
    disengageRange = disengageRange,
    condition = condition, // Self/environment condition check
    targetCondition = targetCondition, // target condition (will be checked on entity interaction)
    description = description,
) {

    private var remainingCooldownTicks = 0
    private val objectiveDescription: String = mainThreadRuntime.resolveString(description)
    override val observedMemories = listOf(MemoryModuleType.HURT_BY_ENTITY, CobblemonMemories.ATTACK_TARGET_DATA)
    private var cryCooldownTicks = 0
    private var proximityAttackCooldown = 0
    private var satiationHitCount = 999
    private var fixation : EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = objectiveDescription

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || entity.isDeadOrDying) return ObjectiveStatus.INVALID
        if (brain.getMemory(MemoryModuleType.AVOID_TARGET) != null) {
            return ObjectiveStatus.BLOCKED
        }

        val attackTarget = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
        if (trackedEntity != null) {
            if (shouldContinue(entity, brain)) {

                // tick behaviour cooldowns
                if (proximityAttackCooldown > 0) proximityAttackCooldown--
                if (cryCooldownTicks > 0) cryCooldownTicks--

                if (attackTarget == null) {
                    // approach, cry, or fight
                    val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
                        .withQueryValue("target", trackedEntity!!.asMostSpecificMoLangValue())
                    val distanceToTarget = entity.distanceTo(trackedEntity!!)
                    val _attackRange = runtime.resolveFloat(attackRange)
                    if (_attackRange > distanceToTarget && proximityAttackCooldown <= 0) {
                        // give em a warning shot
                        val attackDuration = runtime.resolveInt(maxAttackDuration)
                        satiationHitCount = 1
                        brain.setMemory(MemoryModuleType.ATTACK_TARGET, trackedEntity, attackDuration.toLong())
                        fixation = brain.fixate(
                            AttackFixation(
                                targetEntity = trackedEntity!!,
                                speedMultiplier = speedMultiplier,
                                distractionTypes =  setOf(DistractionType.FORCE),
                                availableMoves = getRealTimeMoves(entity)
                            )
                        )
                    } else {
                        // be unhappy about it.
                        if (cryCooldownTicks <= 0) {
                            entity.cry()
                            cryCooldownTicks = CRY_COOLDOWN_TICKS + entity.level().random.nextInt(CRY_COOLDOWN_RANDOM_TICKS)
                        }
                        if (!brain.isFixation(fixation) && brain.fixation !is LookAndLockFixation) {
                            if (brain.distract(Distraction.FORCE)) {
                                fixation = brain.fixate(
                                    LookAndLockFixation(
                                        distractionTypes = setOf(DistractionType.FORCE, DistractionType.DAMAGE),
                                        lookingTracker = LookingTracker.forTargetEntity(entity, trackedEntity!!, trackEyeHeight = true),
                                        condition = { true }
                                    )
                                )
                            }
                        }
                    }
                } else {
                    if (attackTarget.uuid != trackedEntity?.uuid) {
                        // Something has overridden us and independently set the attack target
                        trackedEntity = null
                        return ObjectiveStatus.BLOCKED
                    }
                }
            } else {
                trackedEntity = null
            }
        } else {
            // No active target
            if (remainingCooldownTicks > 0) {
                remainingCooldownTicks--
                return ObjectiveStatus.INACTIVE
            } else {
                remainingCooldownTicks = 20
            }
            val targetEntity = scanForValidTargets(entity, brain)
            if (targetEntity != null) {
                cryCooldownTicks = 0
                proximityAttackCooldown = 0
                trackedEntity = targetEntity
            }

        }
        return if (trackedEntity != null) ObjectiveStatus.ACTIVE else ObjectiveStatus.INACTIVE
    }

    override fun <T> onMemoryChanged(
        entity: LivingEntity,
        brain: BiggerBrain,
        memory: MemoryModuleType<T>,
        value: T?
    ) {
        // TODO: handle conflicts between different behaviours
        if (memory == CobblemonMemories.ATTACK_TARGET_DATA && value != null) {

            val livingEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET) ?: return
            if (trackedEntity?.uuid == livingEntity.uuid) {
                val attackData = value as CobblemonAttackTargetData
                if (attackData.hitsLanded >= satiationHitCount) {
                    // Justice served, give the fool a chance to back off
                    proximityAttackCooldown = PROXIMITY_AGGRO_COOLDOWN_TICKS
                    brain.eraseMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                } else {
                    // We smacked them, refresh our attack timer
                    val runtime = mainThreadRuntime.withQueryValue("entity", (livingEntity).asMostSpecificMoLangValue())
                    val attackDuration = runtime.resolveInt(maxAttackDuration)
                    brain.setMemory(MemoryModuleType.ATTACK_TARGET, livingEntity, attackDuration.toLong())
                }

            }
        } else if (memory == MemoryModuleType.HURT_BY_ENTITY) {
            // Of course you know this means war.
            if (value != null) {
                // Something just smacked us, see if we should get mad about it.
                val livingEntity = value as LivingEntity
                val runtime = mainThreadRuntime.withQueryValue("entity", (entity).asMostSpecificMoLangValue())
                    .withQueryValue("target", livingEntity.asMostSpecificMoLangValue())
                val shouldTarget = runtime.resolveBoolean(condition) && runtime.resolveBoolean(targetCondition)
                if (shouldTarget && brain.distract(Distraction.FORCE)) {
                    brain.setMemory(
                        MemoryModuleType.ATTACK_TARGET, livingEntity,
                        runtime.resolveInt(maxAttackDuration).toLong()
                    )
                    satiationHitCount = 999
                    trackedEntity = livingEntity
                    fixation = brain.fixate(
                        AttackFixation(
                            targetEntity = trackedEntity!!,
                            speedMultiplier = speedMultiplier,
                            distractionTypes =  setOf(DistractionType.FORCE),
                            availableMoves = getRealTimeMoves(entity)
                        )
                    )
                }
            }
        }
    }

    companion object {
        val CRY_COOLDOWN_TICKS = 75
        val CRY_COOLDOWN_RANDOM_TICKS = 50
        val PROXIMITY_AGGRO_COOLDOWN_TICKS = 75
    }

    class Config : ObjectiveConfig {
        val speedMultiplier: ExpressionOrEntityVariable = Either.left("0.5".asExpression())
        val attackRange: ExpressionOrEntityVariable = Either.left("5.0".asExpression())
        val engageRange: ExpressionOrEntityVariable = Either.left("10.0".asExpression())
        val disengageRange: ExpressionOrEntityVariable = Either.left("15.0".asExpression())
        val condition: ExpressionOrEntityVariable = Either.left("true".asExpression())
        val targetCondition: ExpressionOrEntityVariable = Either.left("false".asExpression())
        val maxAttackDuration: ExpressionOrEntityVariable = Either.left("300".asExpression())
        val description: ExpressionOrEntityVariable = Either.left("'Territorial Behaviour'".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): List<MoLangConfigVariable> {
            return listOf(
                condition,
                targetCondition,
                speedMultiplier,
                attackRange,
                engageRange,
                disengageRange,
                maxAttackDuration,
                description
            ).asVariables()
        }

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return TerritorialBehaviour(
                condition = condition.asSimplifiedExpression(entity),
                targetCondition = targetCondition.asSimplifiedExpression(entity),
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
                attackRange = attackRange.asSimplifiedExpression(entity),
                engageRange = engageRange.asSimplifiedExpression(entity),
                disengageRange = disengageRange.asSimplifiedExpression(entity),
                maxAttackDuration = maxAttackDuration.asSimplifiedExpression(entity),
                description = description.asSimplifiedExpression(entity)
            )
        }
    }
}
