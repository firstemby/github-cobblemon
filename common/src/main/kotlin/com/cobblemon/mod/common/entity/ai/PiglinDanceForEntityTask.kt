/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */


import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.google.common.collect.ImmutableMap
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.entity.monster.piglin.Piglin
import java.util.*

class PiglinDanceTarget : Behavior<Piglin>(
    ImmutableMap.of(
        CobblemonMemories.NEAREST_LOVED_ENTITY, MemoryStatus.VALUE_PRESENT,
        MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
    ),
    Int.MAX_VALUE,
    Int.MAX_VALUE
) {
    var lovedPokemon: PokemonEntity? = null
    val wayTooFar = 20F
    var tooFar = 5F
    var closeEnough = 3F
    var tooClose = 1.5F

    override fun checkExtraStartConditions(level: ServerLevel, owner: Piglin): Boolean {
        lovedPokemon = (owner.brain.getMemory(CobblemonMemories.NEAREST_LOVED_ENTITY).orElse(null) ?: return false)
        return lovedPokemon != null
    }

    override fun canStillUse(level: ServerLevel, entity: Piglin, gameTime: Long) : Boolean {
       return  lovedPokemon != null && lovedPokemon?.isAlive == true && (lovedPokemon?.distanceTo(entity) ?: 999F) < wayTooFar
    }


    override fun tick(level: ServerLevel, entity: Piglin, gameTime: Long) {
        val leader : PokemonEntity = lovedPokemon ?: return
        if (entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            return // We're busy goin' n' shit
        }

        if (leader.distanceTo(entity) > tooFar) {
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = leader.blockPosition(),
                    speedModifier = 0.6F,
                    completionRange = closeEnough.toInt()
                )
            )
        } else if (leader.distanceTo(entity) < tooClose) {
            // Give the dude some space
            for (j in 0..9) {
                val vec3 = LandRandomPos.getPosAway(entity, 5, 7, leader.position())
                if (vec3 != null) {
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(
                            vec3,
                            0.4F,
                            closeEnough.toInt()
                        )
                    )
                }
            }
        } else if (leader.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            // Go to the leader's walk target, roughly.
            val walkTarget = leader.brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) ?: return
            entity.brain.setMemory(
                MemoryModuleType.WALK_TARGET,
                CobblemonWalkTarget(
                    pos = walkTarget.target.currentBlockPosition(),
                    speedModifier = walkTarget.speedModifier,
                    nodeTypeFilter = (walkTarget as? CobblemonWalkTarget)?.nodeTypeFilter ?: { true },
                    completionRange = closeEnough.toInt()
                )
            )
        } else {
            // Maybe wander around and mingle
            if (entity.random.nextFloat() < 0.01f) {
                val vec3 = LandRandomPos.getPosTowards(entity,5, 7, leader.position())
                if (vec3 != null) {
                    entity.brain.setMemory(
                        MemoryModuleType.WALK_TARGET,
                        WalkTarget(
                            vec3,
                            0.5F,
                            1
                        )
                    )
                }
            }
        }
    }

    override fun start(level: ServerLevel, entity: Piglin, gameTime: Long) {
        val pokemonEntity = lovedPokemon ?: return
        entity.brain.setMemory(MemoryModuleType.DANCING, true)
        entity.brain.setMemory(MemoryModuleType.CELEBRATE_LOCATION, Optional.of(pokemonEntity.blockPosition()))
        entity.brain.setMemory(MemoryModuleType.LOOK_TARGET, EntityTracker(pokemonEntity, true))
    }

    override fun stop(level: ServerLevel, entity: Piglin, gameTime: Long) {
        entity.brain.setMemory(MemoryModuleType.DANCING, false)
        entity.brain.eraseMemory(MemoryModuleType.CELEBRATE_LOCATION)
    }
}