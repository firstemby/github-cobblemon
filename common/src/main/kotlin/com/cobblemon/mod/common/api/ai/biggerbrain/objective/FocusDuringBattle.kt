/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.BehaviourConfigurationContext
import com.cobblemon.mod.common.api.ai.ExpressionOrEntityVariable
import com.cobblemon.mod.common.api.ai.asVariables
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.EntityFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.LookingTracker
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.Distraction
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.DistractionType
import com.cobblemon.mod.common.api.ai.biggerbrain.distraction.HurtDistraction
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LockFixation
import com.cobblemon.mod.common.api.ai.biggerbrain.fixation.LookingTrackerFixation
import com.cobblemon.mod.common.api.ai.config.objective.ObjectiveConfig
import com.cobblemon.mod.common.api.ai.resolveBoolean
import com.cobblemon.mod.common.api.ai.resolveInt
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.asExpression
import com.mojang.datafixers.util.Either
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType

/**
 * An objective that ensures the entity, whether it's a PokemonEntity or an NPCEntity, will pay attention to its
 * opponents in battle. They will not move, and will look at its opponents. For Pokémon, they will focus on the
 * targeted opponent if one exists; otherwise, they will randomly look at one of their opponent Pokémon. For NPCs,
 * they will randomly look at one of their opponent Pokémon.
 *
 * If the entity gets hurt and [stopBattleIfHurt] is true, this objective stops the battle.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class FocusDuringBattle(
    var stopBattleIfHurt: Boolean = true,
    var minDurationTicks: Int = 40,
    var maxDurationTicks: Int = 80
) : Objective {
    var fixation: EntityFixation? = null

    override fun getDescription(entity: LivingEntity) = "Look at battle opponents"
    override fun perform(entity: LivingEntity, brain: BiggerBrain, activeObjectiveTags: MutableSet<String>): ObjectiveStatus {
        if (entity !is NPCEntity && entity !is PokemonEntity) {
            return ObjectiveStatus.INVALID
        }

        if (stopBattleIfHurt && brain.hasMemoryValue(MemoryModuleType.HURT_BY)) {
            if (brain.isFixation(fixation)) {
                if (entity is PokemonEntity) {
                    entity.battle?.stop()
                } else if (entity is NPCEntity) {
                    entity.battleIds.mapNotNull { BattleRegistry.getBattle(it) }.forEach { it.stop() }
                }
                brain.distract(Distraction.FORCE)
            }
            return ObjectiveStatus.INACTIVE
        }

        val isInBattle = isInBattle(entity)
        if (fixation == brain.fixation) {
            if (isInBattle) {
                // Ok this is fine
                return ObjectiveStatus.FIXATED
            } else {
                // Alright my work here is done, going back to inactive.
                fixation = null
                brain.fixation = null
                return ObjectiveStatus.INACTIVE
            }
        } else if (!isInBattle) {
            // Ok we don't need to do anything
            fixation = null
            return ObjectiveStatus.INACTIVE
        } else {
            // We need to start focusing
            if (brain.distract(Distraction.FORCE)) {
                fixation = if (entity is NPCEntity) NPCFocusFixation(this) else PokemonFocusFixation(this)
                brain.fixation = fixation
                return ObjectiveStatus.FIXATED
            } else {
                return ObjectiveStatus.BLOCKED
            }
        }
    }

    override fun <T> onMemoryChanged(entity: LivingEntity, brain: BiggerBrain, memory: MemoryModuleType<T>, value: T?) {
        if (brain.isFixation(fixation) && memory == MemoryModuleType.HURT_BY && value != null && stopBattleIfHurt) {
            brain.distract(HurtDistraction(value as DamageSource))
        }
    }

    fun isInBattle(entity: LivingEntity): Boolean {
        return when (entity) {
            is PokemonEntity -> entity.isBattling
            is NPCEntity -> entity.isInBattle()
            else -> false
        }
    }

    class NPCFocusFixation(val objective: FocusDuringBattle) : EntityFixation, LookingTrackerFixation {
        override var lookingTracker: LookingTracker? = null
        var ticksUntilReacquireTarget: Int = 0

        override fun getDescription(entity: LivingEntity): String {
            return if (lookingTracker == null) {
                "Looking for an opponent Pokémon to look at"
            } else {
                "Looking at targeted or opponent Pokémon"
            }
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isForce

        fun acquireLookTarget(entity: NPCEntity, brain: BiggerBrain) {
            val battlingPokemon = brain.getMemory(CobblemonMemories.BATTLING_POKEMON) ?: return
            val randomBattlingPokemon = battlingPokemon.randomOrNull()?.let { (entity.level() as ServerLevel).getEntity(it) }
            if (randomBattlingPokemon != null) {
                lookingTracker = LookingTracker(entity, EntityTracker(randomBattlingPokemon, true))
            }
        }

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is NPCEntity) {
                return false
            }

            val battlingPokemon = brain.getMemory(CobblemonMemories.BATTLING_POKEMON) ?: return false

            val lookingTracker = lookingTracker
            if (lookingTracker != null && lookingTracker.entity.uuid !in battlingPokemon) {
                this.lookingTracker = null
                ticksUntilReacquireTarget = 0
            }

            if (ticksUntilReacquireTarget <= 0) {
                acquireLookTarget(entity, brain)
                ticksUntilReacquireTarget = (objective.minDurationTicks..objective.maxDurationTicks).random()
            } else {
                ticksUntilReacquireTarget--
            }

            lookingTracker?.tick()

            return true
        }
    }

    class PokemonFocusFixation(val objective: FocusDuringBattle) : EntityFixation, LookingTrackerFixation, LockFixation {
        var ticksUntilReacquireTarget: Int = 0
        override var lookingTracker: LookingTracker? = null

        override fun getDescription(entity: LivingEntity): String {
            return if (lookingTracker == null) {
                "Looking for an opponent Pokémon to look at"
            } else {
                "Looking at targeted or opponent Pokémon"
            }
        }

        override fun canDistract(distraction: Distraction, entity: LivingEntity) = distraction.isForce

        override fun tick(entity: LivingEntity, brain: BiggerBrain): Boolean {
            if (entity !is PokemonEntity) {
                return false
            }

            val battlingPokemon = brain.getMemory(CobblemonMemories.BATTLING_POKEMON) ?: return false
            val targetedBattlePokemon = brain.getMemory(CobblemonMemories.TARGETED_BATTLE_POKEMON)

            val lookingTracker = lookingTracker
            // If there is a targetedBattlePokemon then that is the highest priority, and we should not look away
            // Otherwise, look at one of the entities for a while and occasionally reacquire the target.
            if (targetedBattlePokemon != null && (lookingTracker == null || lookingTracker.entity.uuid != targetedBattlePokemon)) {
                // We should be targeting the targetedBattlePokemon
                val targetedPokemon = (entity.level() as ServerLevel).getEntity(targetedBattlePokemon) ?: return false
                this.lookingTracker = LookingTracker(entity, EntityTracker(targetedPokemon, true))
                ticksUntilReacquireTarget = (objective.minDurationTicks..objective.maxDurationTicks).random()
            } else if (targetedBattlePokemon == null && ticksUntilReacquireTarget <= 0) {
                // Choose someone to look at
                val randomBattlingPokemon = battlingPokemon.randomOrNull()?.let { (entity.level() as ServerLevel).getEntity(it) }
                if (randomBattlingPokemon != null) {
                    this.lookingTracker = LookingTracker(entity, EntityTracker(randomBattlingPokemon, true))
                    ticksUntilReacquireTarget = (objective.minDurationTicks..objective.maxDurationTicks).random()
                }
            }

            ticksUntilReacquireTarget--
            this.lookingTracker?.tick()
            return true
        }
    }

    class Config : ObjectiveConfig {
        var stopBattleIfHurt: ExpressionOrEntityVariable = Either.left("true".asExpression())
        var minDurationTicks: ExpressionOrEntityVariable = Either.left("40".asExpression())
        var maxDurationTicks: ExpressionOrEntityVariable = Either.left("80".asExpression())

        override fun getVariables(
            entity: LivingEntity,
            behaviourConfigurationContext: BehaviourConfigurationContext
        ) = listOf(
            stopBattleIfHurt,
            minDurationTicks,
            maxDurationTicks
        ).asVariables()

        override fun create(entity: LivingEntity, behaviourConfigurationContext: BehaviourConfigurationContext): Objective {
            val stopBattleIfHurt = stopBattleIfHurt.resolveBoolean(behaviourConfigurationContext.runtime)
            val minDurationTicks = minDurationTicks.resolveInt(behaviourConfigurationContext.runtime)
            val maxDurationTicks = maxDurationTicks.resolveInt(behaviourConfigurationContext.runtime)
            return FocusDuringBattle(
                stopBattleIfHurt = stopBattleIfHurt,
                minDurationTicks = minDurationTicks,
                maxDurationTicks = maxDurationTicks
            )
        }
    }
}