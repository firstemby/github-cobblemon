 /*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities
import net.minecraft.world.entity.ai.sensing.Sensor

/**
 * Senses when the Pokémon is near an adult of the same species.
 *
 * @author JazzMcNade
 * @since May 15th, 2024
 */
class PokemonPiglinLovedSensor : Sensor<LivingEntity>(100) {
    override fun requires() = setOf(CobblemonMemories.NEAREST_LOVED_ENTITY, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)

    override fun doTick(world: ServerLevel, entity: LivingEntity) {
        entity.brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).ifPresent { visibleMobs ->
            setNearestPokemonAdult(entity, visibleMobs)
        }
    }

    private fun setNearestPokemonAdult(entity: LivingEntity, visibleMobs: NearestVisibleLivingEntities) {
        val nearestGoldenIdol = visibleMobs.findClosest { mob ->
            mob is PokemonEntity && mob.exposedSpecies.behaviour.entityInteract.lovedByPiglin
        }.map { mob -> mob as PokemonEntity }

        entity.brain.setMemory(CobblemonMemories.NEAREST_LOVED_ENTITY, nearestGoldenIdol)
    }
}