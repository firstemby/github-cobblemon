/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai

import com.cobblemon.mod.common.api.serialization.StringIdentifiedObjectAdapter
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity

/**
 * How deeply a Pokémon sleeps. This takes the current situation and decides if a Pokémon should fall asleep or wake.
 *
 * A depth should be registered by name in [SleepDepth.depths].
 *
 * @author Hiroku
 * @since July 17th, 2022
 */
interface SleepDepth {
    companion object {
        val comatose = object : SleepDepth {
            override fun canSleep(pokemonEntity: PokemonEntity) = true
            override fun shouldWake(pokemonEntity: PokemonEntity) = true
        }

        val normal = object : SleepDepth {
            override fun canSleep(pokemonEntity: PokemonEntity): Boolean {
                return pokemonEntity.level().players().none { player ->
                    player.isAlive && !player.isSpectator && player.distanceToSqr(pokemonEntity) < 16.0 * 16.0
                }
            }

            override fun shouldWake(pokemonEntity: PokemonEntity): Boolean {
                return pokemonEntity.level().players().any { player ->
                    player.isAlive && !player.isSpectator && player.distanceToSqr(pokemonEntity) < 16.0 * 16.0 && !player.isCrouching
                }
            }
        }

        val depths = mutableMapOf(
            "comatose" to comatose,
            "normal" to normal
        )

        val adapter = StringIdentifiedObjectAdapter({ depths[it] ?: throw IllegalArgumentException("Unknown sleep depth: $it") })
    }

    fun canSleep(pokemonEntity: PokemonEntity): Boolean
    fun shouldWake(pokemonEntity: PokemonEntity): Boolean
}
