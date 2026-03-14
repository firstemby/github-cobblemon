/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective


import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementStatus
import com.cobblemon.mod.common.api.ai.biggerbrain.MovementTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.idleBoredomDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.MovementTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveFloat
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.RunPosableMoLangPacket
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Stops movement and plays a Pokemon's dance animation, if available.
 *
 */
class Dance(
    val minDuration: Int,
    val danceChance: Float
) : Objective {
    var fixation: EntityFixation? = null
    var remainingCooldownTicks = 0
    override fun getDescription(entity: LivingEntity) = "Dancing"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(fixation)) {
            remainingCooldownTicks = ticksBetweenAttempts
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation != null) {
            fixation = null
        }

        if (remainingCooldownTicks > 0) {
            remainingCooldownTicks--
            return ObjectiveStatus.INACTIVE
        }

        // Only dance if we feel like it
        val danceMemory = brain.getMemory(MemoryModuleType.DANCING) ?: false
        if (!danceMemory || entity.random.nextFloat() > danceChance || entity.ticksLived < 4 * 20) {
            return ObjectiveStatus.INACTIVE
        }

        if (!brain.distract(idleBoredomDistraction)) {
            return ObjectiveStatus.BLOCKED
        }

        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.navigation.stop()


        // Become fixated
        fixation = brain.fixate(
            DanceFixation(
                this,
                null,
                minDuration
            )
        )
        return ObjectiveStatus.ACTIVE
    }


    class DanceFixation(
        val objective: Dance,
        override val movementTracker: MovementTracker?,
        val minDuration: Int
    ) : EntityFixation, MovementTrackerFixation {
        var ticksSinceStarted = 0
        override fun getDescription(entity: LivingEntity) = "Dancing"
        override fun tickMovementFixation() = MovementStatus.PENDING

        override fun onDistract(distraction: Distraction, entity: LivingEntity) {
            super.onDistract(distraction, entity)
            stopDancing(entity)
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            if (distraction.isIdleBoredom) {
                return false
            }
            return true
        }

        fun stopDancing(entity: LivingEntity) {
            val pokemon = entity as? PokemonEntity ?: return
            pokemon.setBehaviourFlag(PokemonBehaviourFlag.DANCING, false)
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (ticksSinceStarted == 0) { // Makes make me want a Start() for fixations
                val pokemon = entity as? PokemonEntity ?: return false
                RunPosableMoLangPacket(entity.id, setOf("v.bpm = 100;")).sendToPlayersAround(entity, 64.0)
                pokemon.setBehaviourFlag(PokemonBehaviourFlag.DANCING, true)
            }
            ticksSinceStarted++
            if (ticksSinceStarted > minDuration && !entity.brain.getMemorySafely(MemoryModuleType.DANCING).orElse(false)) {
                stopDancing(entity)
                return false
            }
            return true
        }
    }

    class Config : ObjectiveConfig {
        var danceChance: ExpressionOrEntityVariable = Either.left("1/2".asExpression())
        var minDuration: ExpressionOrEntityVariable = Either.left("60".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            minDuration,
            danceChance
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            val minDuration = minDuration.resolveInt(behaviourConfigurationContext.runtime)
            val danceChance = danceChance.resolveFloat(behaviourConfigurationContext.runtime)
            return Dance(
                minDuration,
                danceChance)
        }
    }

    companion object {
        private const val ticksBetweenAttempts = 20
    }
}