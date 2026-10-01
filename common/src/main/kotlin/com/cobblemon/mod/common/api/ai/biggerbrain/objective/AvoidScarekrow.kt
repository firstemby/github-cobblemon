/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.Priority
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.RegularMovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.ConditionalLockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookAndMoveFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMostSpecificMoLangValue
import com.cobblemon.mod.common.api.npc.configuration.MoLangConfigVariable
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import com.cobblemon.mod.common.util.sendParticlesServer
import com.cobblemon.mod.common.util.toVec3d
import com.cobblemon.mod.common.util.withQueryValue
import com.mojang.datafixers.util.Either
import net.minecraft.core.BlockPos
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.phys.Vec3

class AvoidScarekrow(
    private val speedMultiplier: Expression = "0.5".asExpression(),
) : Objective {
    private var movementTask: EntityFixation? = null
    private var remainingCooldownTicks = 0

    override val observedMemories = listOf(CobblemonMemories.NEARBY_SCAREKROW)

    override fun getDescription(entity: LivingEntity) = "Avoiding target block"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val target = brain.getMemory(CobblemonMemories.NEARBY_SCAREKROW)
            ?: return ObjectiveStatus.INACTIVE

        // if the scarekrow is gone we dont need to be scared anymore
        if (!entity.level().getBlockState(target).`is`(CobblemonBlockTags.SCAREKROWS)) {
            brain.eraseMemory(CobblemonMemories.NEARBY_SCAREKROW)
            return ObjectiveStatus.INACTIVE
        }

        // finish existing movement task
        if (brain.isFixation(movementTask)) {
            return ObjectiveStatus.FIXATED
        }

        // aggro against the player should take priority
        if (brain.getMemory(MemoryModuleType.ATTACK_TARGET) != null) {
            return ObjectiveStatus.BLOCKED
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
        }
        val runtime = mainThreadRuntime.withQueryValue("entity", entity.asMostSpecificMoLangValue())
        val speed = runtime.resolveFloat(speedMultiplier)

        if (!brain.distract(Distraction.FORCE)) return ObjectiveStatus.BLOCKED
        var targetPos : BlockPos? = null

        // fixate on running from the scarekrow until it is out of range
        brain.fixate(ConditionalLockFixation("Avoiding scarekrow") {
            brain.getMemory(CobblemonMemories.NEARBY_SCAREKROW) != null
        })

        // find position
        val direction = entity.position().subtract(target.toVec3d())
        for (j in 0..9) {
            val targetVec3 = LandRandomPos.getPosAway(entity, (Cobblemon.config.scarekrowRadius * 1.5).toInt(), 7, direction)
            if (targetVec3 != null) {
                targetPos = BlockPos.containing(targetVec3)
                break
            }
        }

        // Start a look-and-move fixation only when navigation accepts the selected position.
        if (targetPos != null) {
            val movementTracker = RegularMovementTracker.attempt(
                entity = entity,
                walkTarget = WalkTarget(
                    targetPos,
                    speed,
                    0
                )
            )
            entity.level().sendParticlesServer(ParticleTypes.HAPPY_VILLAGER, targetPos.above().toVec3d(), 5, Vec3(0.0, 0.0, 0.0), 5.0)
            if (movementTracker != null) {
                movementTask = brain.fixate(
                    LookAndMoveFixation(
                        distractionTypes = setOf(
                            DistractionType.FORCE
                        ),
                        lookingTracker = LookingTracker.fromNavigationPathOrWalkTarget(
                            entity,
                            movementTracker.walkTarget
                        ),
                        movementTracker = movementTracker,
                        description = "Moving away from target block",
                    )
                )
                return ObjectiveStatus.FIXATED
            }
        }

        return ObjectiveStatus.FIXATED
    }

    override fun onRegister(entity: LivingEntity, brain: BiggerBrain) {
        brain.subscribe(
            priority = Priority.LOW,
            memory = CobblemonMemories.NEARBY_SCAREKROW,
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
            return AvoidScarekrow(
                speedMultiplier = speedMultiplier.asSimplifiedExpression(entity),
            )
        }
    }
}
