/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.fixation

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.CobblemonAttackTargetData
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.RealTimeMove
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.withQueryValue
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.GenericHurtingProjectile
import kotlin.collections.set

class AttackFixation(
    val targetEntity: LivingEntity,
    /** Distraction types that will be respected. */
    private val speedMultiplier: Expression = "1.0".asExpression(), // TODO: Dynamically override movement speed for different aggro behav
    private val availableMoves: List<RealTimeMove> = emptyList(),
    val distractionTypes: Set<DistractionType>,
) : EntityFixation {

    private val globalAttackCooldownTicks: Int = 20 // TODO: pull from pokemon stats
    var movementTracker: MovementTracker? = null
    var lookingTracker: LookingTracker? = null
    override fun getDescription(entity: LivingEntity): String {
        return "Attacking Target"
    }

    override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
        return distraction.types.any(distractionTypes::contains)
    }

    override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
        if (entity !is PokemonEntity || !entity.isAlive) return false
        entity as PokemonEntity // Intellij and my brain says this is redundant but I'm not getting smart casting later on sooo
        val targetedEntity = brain.getMemory(MemoryModuleType.ATTACK_TARGET)
            ?: return false

        if (targetedEntity.isRemoved || targetedEntity.isDeadOrDying || brain.getMemory(MemoryModuleType.AVOID_TARGET) != null) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return false
        }

        if (targetedEntity is Player && (targetedEntity.isCreative || targetedEntity.isSpectator)) {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
            brain.eraseMemory(MemoryModuleType.ANGRY_AT)
            return false
        }

        // If we have a current target, check if it's still valid and within roam range (if tethered)
        if (targetedEntity.isAlive) {
            val tethering = (entity).tethering
            if (tethering != null && !tethering.canRoamTo(targetedEntity.blockPosition())) {
                // Target moved out of range let's kill the memory >:)
                brain.distract(Distraction.FORCE)
                entity.brain.eraseMemory(MemoryModuleType.ATTACK_TARGET)
                return false
            }
        }


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
                if (move.id !in entity.pokemon.realTimeMoveCooldowns && move.priority < highestPriority && (distanceToTarget <= move.range || entity.isWithinMeleeAttackRange(
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
                    entity.level().addFreshEntity(projectile)

                    // TODO: add this as an "on hit" post event on the project
                    var attackTargetData = brain.getMemory(CobblemonMemories.ATTACK_TARGET_DATA)
                    if (attackTargetData != null) {
                        attackTargetData.hitsLanded += 1

                    } else {
                        attackTargetData = CobblemonAttackTargetData()
                        attackTargetData.hitsLanded = 1
                    }
                    brain.setMemory(CobblemonMemories.ATTACK_TARGET_DATA, attackTargetData)
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
                entity.pokemon.realTimeMoveCooldowns[selectedMove.id] = selectedMove.cooldownTicks
                brain.setMemory(MemoryModuleType.ATTACK_COOLING_DOWN, true, globalAttackCooldownTicks.toLong())
            }

        }

        if (lookingTracker != null) {
            lookingTracker!!.tick()
        }

        if (movementTracker != null) {
            val movementStatus = movementTracker!!.tick()
            if (movementStatus != MovementStatus.PENDING) {
                movementTracker!!.stop()
                movementTracker = null
            }
        } else {
            // Not moving right now and we need to figure out to what distance to the enemy we want to move to
            var idealDistanceToTarget = Float.MAX_VALUE
            var highestPriority = Int.MAX_VALUE
            availableMoves.forEachIndexed { index, move ->
                // We want the highest priority move that's not on cooldown
                if (move.id !in entity.pokemon.realTimeMoveCooldowns && move.priority < highestPriority) {
                    highestPriority = move.priority
                    idealDistanceToTarget = move.range
                }
            }

            // Get distance to target if we didn't initialize it in the earlier block
            if (distanceToTarget < 0) {
                distanceToTarget = entity.distanceTo(targetedEntity)
            }
            if (idealDistanceToTarget < distanceToTarget) {
                val walkTarget = WalkTarget(EntityTracker(targetedEntity, true), speed, idealDistanceToTarget.toInt())
                movementTracker = RegularMovementTracker.attempt(entity, walkTarget)
                lookingTracker = LookingTracker.forTargetEntity(entity, targetedEntity, trackEyeHeight = true)
            } else if (lookingTracker == null) {
                // Fixate at looking at the target so we don't wander off
                lookingTracker = LookingTracker.forTargetEntity(entity, targetedEntity, trackEyeHeight = true)

            }
        }
        return true
    }

}