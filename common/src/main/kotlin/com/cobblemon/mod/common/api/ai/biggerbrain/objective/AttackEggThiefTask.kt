/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain.objective

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.ai.biggerbrain.BiggerBrain
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.Objective
import com.cobblemon.mod.common.api.ai.biggerbrain.objective.ObjectiveStatus
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.player.Player

class AttackEggThiefTask : Objective {
    private var remainingCooldownTicks = 0
    private var lastThiefId: java.util.UUID? = null

    override fun getDescription(entity: LivingEntity) = "Attack egg thief"

    override fun perform(
        entity: LivingEntity,
        brain: BiggerBrain,
        activeObjectiveTags: MutableSet<String>
    ): ObjectiveStatus {
        if (entity !is PokemonEntity || !entity.isAlive) return ObjectiveStatus.INVALID

        val eggThief = brain.getMemory(CobblemonMemories.EGG_THIEF) as? Player ?: run {
            lastThiefId = null
            return ObjectiveStatus.INACTIVE
        }

        activeObjectiveTags.add(Objective.OBJECTIVE_TAG_WANDERING)

        if (remainingCooldownTicks > 0 && lastThiefId == eggThief.uuid) {
            remainingCooldownTicks--
            return ObjectiveStatus.ACTIVE
        }

        brain.sendDebugMessage("[AttackEggThief] Attacking egg thief ${eggThief.name.string}.")
        entity.cry()
        (entity.level() as? ServerLevel)?.sendParticles(
            net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
            entity.getRandomX(1.0),
            entity.getRandomY() + 0.5,
            entity.getRandomZ(1.0),
            4 + kotlin.random.Random.nextInt(2),
            0.2 * entity.random.nextGaussian(),
            0.1 * entity.random.nextGaussian(),
            0.2 * entity.random.nextGaussian(),
            0.02
        )

        brain.eraseMemory(CobblemonMemories.EGG_SEARCH_COUNTDOWN)
        brain.setMemory(MemoryModuleType.ANGRY_AT, eggThief.uuid)
        brain.setMemory(MemoryModuleType.ATTACK_TARGET, eggThief)

        lastThiefId = eggThief.uuid
        remainingCooldownTicks = 40

        return ObjectiveStatus.ACTIVE
    }
}
