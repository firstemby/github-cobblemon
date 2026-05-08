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
import com.cobblemon.mod.common.api.ai.*
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndLockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.RealTimeMove
import com.cobblemon.mod.common.util.*
import com.mojang.datafixers.util.Either
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.GenericHurtingProjectile

/*
    Generic realtime Attack Objective.
    A mon has a list of available attacks, with a given range, priority, and independent cooldown ticks.
    A mon will move into the range of the attack with the highest priority that's not on cooldown.
    Any time an attack is off cooldown and in range, it will attempt it.

    TODO: Need a fixation for when the pokemon isn't moving or attacking
    TODO: Basic melee/range attack for pokemon that don't have moves specified.
 */

class AttackTarget(
    private val speedMultiplier: Expression = "1.0".asExpression(), // TODO: Dynamically override movement speed for different aggro behav
    private val attackRange: Expression = "2.0".asExpression(), // TODO: Dynamically override min/max engagement ranges for different aggro behaviours
    private val globalAttackCooldownTicks: Int = 20,
    private val availableMoves: List<RealTimeMove> = emptyList()
) : Objective {
    private var movementTask: EntityFixation? = null
//    private var remainingCooldownTicks = 0

    // TODO: Storing the move cooldowns here moves means that they will be reset when the pokemon is recalled or if the world is saved/reloaded
    //  Also I'm mapping moves to cooldowns via array indices, which feels hazardous
    val moveCooldowns: MutableList<Int> = MutableList(availableMoves.count()) { it -> 0 }
    override val observedMemories = listOf(MemoryModuleType.ATTACK_TARGET)

    override fun getDescription(entity: LivingEntity) = "Attack target"

    fun tickMoveCooldowns() {
        for (i in moveCooldowns.indices) {
            if (moveCooldowns[i] > 0) {
                --moveCooldowns[i]
            }
        }
    }

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        tickMoveCooldowns()

        val targetedEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
            ?: return ObjectiveStatus.INACTIVE

        if (targetedEntity.isRemoved || targetedEntity.isDeadOrDying) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return ObjectiveStatus.INACTIVE
        }

        if (targetedEntity is Player && (targetedEntity.isCreative || targetedEntity.isSpectator)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return ObjectiveStatus.INACTIVE
        }

        // If we have a current target, check if it's still valid and within roam range (if tethered)
        if (targetedEntity.isAlive) {
            val tethering = (entity).tethering
            if (tethering != null && !tethering.canRoamTo(targetedEntity.blockPosition())) {
                // Target moved out of range let's kill the memory >:)
                brain.distract(Distraction.FORCE)
                entity.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            }
        }

        // TODO: Does nothing atm, if we can find a way to not need to do all of this every tick
//        if (remainingCooldownTicks > 0) {
//            remainingCooldownTicks--
//        }

        var runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        runtime = runtime.withQueryValue("target", targetedEntity.asMostSpecificMoLangValue())
//        val range = runtime.resolveFloat(attackRange)
        val speed = runtime.resolveFloat(speedMultiplier)

        var selectedMoveIndex = -1
        var distanceToTarget = -1.0f

        if (!(entity.biggerBrain.getMemory(MemoryModuleType.ATTACK_COOLING_DOWN) ?: false)) {
            // Not blocked by global cooldown, check if any available moves can be performed
            var highestPriority = Int.MAX_VALUE
            distanceToTarget = entity.distanceTo(targetedEntity)
            availableMoves.forEachIndexed { index, move ->
                // check for cooldown ticks, priority, and range
                // TODO: Account for hitbox sizes when doing range checks
                if (moveCooldowns[index] == 0 && move.priority < highestPriority && (distanceToTarget <= move.range || entity.isWithinMeleeAttackRange(
                        targetedEntity
                    ))
                ) {
                    selectedMoveIndex = index
                    highestPriority = move.priority
                }
            }
            if (selectedMoveIndex != -1) {
                val selectedMove = availableMoves[selectedMoveIndex]

                // TODO: Each of these could be their own fixations, allowing us do to wind up/recovery timings for attacks
                if (selectedMove.isProjectile) {
                    val velocity = targetedEntity.eyePosition.subtract(entity.eyePosition).normalize().scale(3.0)
                    val projectile = GenericHurtingProjectile(
                        entity.level(),
                        entity,
                        velocity,
                        selectedMove.power * 1.0f,
                        selectedMove.effect
                    )
                    projectile.setPos(entity.eyePosition)
                    entity.level().addFreshEntity(projectile);
                } else {
                    // Dish out the damage
                    // TODO: Do damage calculation based on the Pokemon's stats * power of the attack
                    //      Probably override doHurtTarget in PokemonEntity
                    if (entity.doHurtTarget(targetedEntity)) {
                        entity.swing(InteractionHand.MAIN_HAND)

                        // if hit succeeded, apply additional effect
                        runtime.execute(selectedMove.effect)
                        var attackTargetData = brain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                        if (attackTargetData != null) {
                            attackTargetData.hitsLanded += 1

                        } else {
                            attackTargetData = CobblemonAttackTargetData()
                            attackTargetData.hitsLanded = 1
                        }
                        brain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
                    }
                }


                // Reset cooldowns
                moveCooldowns[selectedMoveIndex] = selectedMove.cooldownTicks
                brain.setMemory(MemoryModuleType.ATTACK_COOLING_DOWN, true, globalAttackCooldownTicks.toLong())
            }

        }


        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        // Not moving right now and we need to figure out to what distance to the enemy we want to move to
        var idealDistanceToTarget = Float.MAX_VALUE
        var highestPriority = Int.MAX_VALUE
        availableMoves.forEachIndexed { index, move ->
            // We want the highest priority move that's not on cooldown
            if (moveCooldowns[index] == 0 && move.priority < highestPriority) {
                highestPriority = move.priority
                idealDistanceToTarget = move.range
            }
        }

        // Get distance to target if we didn't initialize it in the earlier block
        if (distanceToTarget < 0) {
            distanceToTarget = entity.distanceTo(targetedEntity)
        }

        if (idealDistanceToTarget < distanceToTarget) {
            if (!brain.distract(Distraction.FORCE)) return ObjectiveStatus.BLOCKED
//            val from: Vec3 = entity.position()
//            val to: Vec3 = targetedEntity.position()
//            // Direction from mob -> target
//            val dir = to.subtract(from).normalize()
//            val walkPos = to.subtract(dir.scale(max(0.0,  idealDistanceToTarget.toDouble())))

            // TODO: more precise targeting, WalkTarget is often not precise enough to my liking
//            val walkTarget = WalkTarget(
//                BlockPos(walkPos.x.toInt(), walkPos.y.toInt(), walkPos.z.toInt()),
//                1.0f,  // speed modifier
//                0 // completion range
//            )
            val walkTarget = WalkTarget(EntityTracker(targetedEntity, true), speed, idealDistanceToTarget.toInt())
            val movementTracker = RegularMovementTracker.attempt(entity, walkTarget) ?: return ObjectiveStatus.ACTIVE
            movementTask = brain.fixate(
                LookAndMoveFixation(
                    distractionTypes = setOf(DistractionType.FORCE),
                    lookingTracker = LookingTracker.forTargetEntity(entity, targetedEntity, trackEyeHeight = true),
                    movementTracker = movementTracker,
                    description = "Moving to attack target"
                )
            )
        } else if (brain.fixation !is LookFixation) {
            // TODO: Look Fixation
            // Fixate at looking at the target so we don't wander off
            brain.fixate(
                LookAndLockFixation(
                    distractionTypes = setOf(DistractionType.FORCE),
                    lookingTracker = LookingTracker.forTargetEntity(entity, targetedEntity, trackEyeHeight = true),
                    condition = { entity.biggerBrain.getMemory(MemoryModuleType.ATTACK_TARGET) != null }
                )
            )
        }



        return ObjectiveStatus.ACTIVE
    }

    override fun <T> onMemoryChanged(
        entity: LivingEntity,
        brain: BiggerBrain,
        memory: MemoryModuleType<T>,
        value: T?
    ) {
        if (memory == MemoryModuleType.ATTACK_TARGET) {
            if (value == null) {
                if (brain.isFixation(movementTask)) {
                    brain.distract(Distraction.FORCE)
                    movementTask = null
                }
            }
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
                globalAttackCooldownTicks = behaviourConfigurationContext.runtime.resolveInt(
                    attackCooldownTicks.asSimplifiedExpression(entity)
                ),
                availableMoves = if (entity is PokemonEntity) entity.pokemon.species.behaviour.combat.moves else emptyList()
            )
        }
    }
}
