/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.sensors

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.client.effect.SpawnSnowstormEntityParticlePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.requirements.LevelRequirement
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.getMemorySafely
import com.cobblemon.mod.common.util.party
import java.util.UUID
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.sensing.Sensor

class AlphaLevelMatchingSensor : Sensor<PokemonEntity>(20) {
    private var lastMatchingPlayerId: UUID? = null

    override fun requires() = setOf(MemoryModuleType.NEAREST_PLAYERS)

    override fun doTick(world: ServerLevel, entity: PokemonEntity) {
        if (!entity.pokemon.isAlpha || !entity.pokemon.isWild() || entity.isBattling) {
            lastMatchingPlayerId = null
            return
        }

        val nearestPlayer = entity.brain.getMemorySafely(MemoryModuleType.NEAREST_PLAYERS)
            .orElse(emptyList())
            .filterIsInstance<ServerPlayer>()
            .minByOrNull { it.distanceToSqr(entity) }

        if (nearestPlayer == null || nearestPlayer.distanceToSqr(entity) > RADIUS_SQR) {
            lastMatchingPlayerId = null
            return
        }

        if (lastMatchingPlayerId != nearestPlayer.uuid) {
            lastMatchingPlayerId = nearestPlayer.uuid
            entity.cry()

            SpawnSnowstormEntityParticlePacket(cobblemonResource("statup_actor"), entity.id, listOf("middle", "root"))
                .sendToPlayersAround(entity.x, entity.y, entity.z, 64.0, world.dimension())
        }

        val strongestPartyLevel = nearestPlayer.party().maxOfOrNull { it.level } ?: return
        val minEvolutionLevel = minimumEvolutionLevel(entity.pokemon)
        val targetLevel = when {
            strongestPartyLevel < 20 -> strongestPartyLevel + 5
            strongestPartyLevel in 31..40 -> strongestPartyLevel + 10
            strongestPartyLevel in 51..65 -> strongestPartyLevel + 15
            strongestPartyLevel >= 66 -> strongestPartyLevel + 20
            else -> strongestPartyLevel
        }.coerceAtMost(100).coerceAtLeast(minEvolutionLevel)

        if (entity.pokemon.level != targetLevel) {
            entity.pokemon.level = targetLevel
        }
    }

    // we do not want an Alpha to go below their minimum level requirement....
    private fun minimumEvolutionLevel(pokemon: Pokemon): Int {
        val preEvolution = pokemon.preEvolution ?: return 1
        val currentSpecies = pokemon.species.resourceIdentifier
        val isStandardForm = pokemon.form == pokemon.species.standardForm
        val currentFormId = pokemon.form.formOnlyShowdownId()
        var floor = Int.MAX_VALUE

        // make sure we safely grab the correct pre-evo to get the evolution number value from
        for (evolution in preEvolution.form.evolutions) {
            val resultSpecies = evolution.result.species ?: continue
            if (!resultSpecies.equals(currentSpecies.path, ignoreCase = true) &&
                !resultSpecies.equals(currentSpecies.toString(), ignoreCase = true)
            ) {
                continue
            }

            val resultForm = evolution.result.form
            if (resultForm == null && !isStandardForm) {
                continue
            }
            if (resultForm != null &&
                !resultForm.equals(currentFormId, ignoreCase = true) &&
                !resultForm.equals(pokemon.form.name, ignoreCase = true)
            ) {
                continue
            }

            var minLevel = 1
            for (requirement in evolution.requirements) {
                if (requirement is LevelRequirement && requirement.minLevel > minLevel) {
                    minLevel = requirement.minLevel
                }
            }
            if (minLevel < floor) {
                floor = minLevel
            }
        }

        return if (floor == Int.MAX_VALUE) 1 else floor
    }

    companion object {
        private const val RADIUS_SQR = 32.0 * 32.0
    }
}
