/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.codec.internal

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.*
import net.minecraft.resources.ResourceLocation

internal data class PokemonP4(
    val originalTrainerFriendship: Optional<Int>,
    val realTimeMoveCooldowns: Map<ResourceLocation, Int>,
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        this.originalTrainerFriendship.ifPresent { other.originalTrainerFriendship = it }
        other.refreshOriginalTrainer()
        other.realTimeMoveCooldowns = this.realTimeMoveCooldowns.toMutableMap()
        return other
    }

    companion object {
        internal val CODEC: MapCodec<PokemonP4> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.INT.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER_FRIENDSHIP).forGetter(PokemonP4::originalTrainerFriendship),
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf(DataKeys.POKEMON_REAL_TIME_MOVE_COOLDOWNS, emptyMap<ResourceLocation, Int>()).forGetter(PokemonP4::realTimeMoveCooldowns),
            ).apply(instance) {
              originalTrainerFriendship,
              realTimeMoveCooldowns -> PokemonP4(
                originalTrainerFriendship,
                realTimeMoveCooldowns,
              )
            }
        }

        internal fun from(pokemon: Pokemon): PokemonP4 = PokemonP4(
            Optional.ofNullable(pokemon.originalTrainerFriendship),
            pokemon.realTimeMoveCooldowns,
        )
    }
}
