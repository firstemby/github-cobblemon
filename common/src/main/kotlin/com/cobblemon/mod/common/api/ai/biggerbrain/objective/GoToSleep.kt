/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asSimplifiedExpression
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.api.storage.party.PartyStore
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveBoolean
import com.mojang.datafixers.util.Either
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * Randomly looks at nearby entities out of curiosity. Very easily distracted once doing so.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class GoToSleep(
    val onlyFromStatus: Expression = "false".asExpression(),
) : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Sleeping"
    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        } else if (brain.isFixation(fixation)) {
            return ObjectiveStatus.FIXATED
        } else if (brain.fixation != null) {
            fixation = null
            return ObjectiveStatus.BLOCKED
        }

        val isDrowsy = brain.getMemory(CobblemonMemories.POKEMON_DROWSY)
            ?: return ObjectiveStatus.INVALID

        brain.getMemory(MemoryModuleType.HURT_BY)
        val hasSleepStatus = entity.pokemon.status?.status === Statuses.SLEEP
        if (entity.behaviour.resting.canSleep && entity.canSleepAt(entity.blockPosition().below()) || hasSleepStatus) {
            if (!hasSleepStatus) {
                if (mainThreadRuntime.resolveBoolean(onlyFromStatus)) {
                    return ObjectiveStatus.BLOCKED
                }
                entity.pokemon.status = PersistentStatusContainer(Statuses.SLEEP)
            }
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.navigation.stop()
            entity.brain.setActiveActivityToFirstValid(listOf(CobblemonActivities.POKEMON_SLEEPING_ACTIVITY))
            entity.brain.setMemory(CobblemonMemories.POKEMON_SLEEPING, true)

            // Become fixated
            fixation = brain.fixate(
                SleepFixation(
                    this,
                )
            )
            return ObjectiveStatus.FIXATED
        }
        return ObjectiveStatus.BLOCKED
    }



    class SleepFixation(
        val objective: GoToSleep,
    ) : EntityFixation {

        override fun getDescription(entity: LivingEntity): String {
            return "Sleeping"
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity): Boolean {
            // Bro literally anything will distract me
            if (distraction.isIdleBoredom) {
                return false
            }
            wakeUp(entity)
            return true
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (!entity.brain.getMemorySafely(CobblemonMemories.POKEMON_DROWSY).orElse(false)) {
                wakeUp(entity)
                return false
            }
            return true
        }
        fun wakeUp(entity: LivingEntity) {
            if (entity !is PokemonEntity) return
            val hasSleepStatus = entity.pokemon.status?.status == Statuses.SLEEP
            if (hasSleepStatus && entity.pokemon.storeCoordinates.get()?.store is PartyStore) {
                return // You can't wake up if you're in the party with the sleep status.
            }
            if (hasSleepStatus) {
                entity.pokemon.status = null
            }
            entity.brain.eraseMemory(CobblemonMemories.POKEMON_SLEEPING)
            entity.brain.useDefaultActivity()

        }
    }

    class Config : ObjectiveConfig {

        val onlyFromStatus: ExpressionOrEntityVariable = Either.left("false".asExpression())
        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            onlyFromStatus
        ).asVariables()

        override fun create(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ): Objective {
            return GoToSleep(
                onlyFromStatus = onlyFromStatus.asSimplifiedExpression(entity)
            )
        }
    }
}