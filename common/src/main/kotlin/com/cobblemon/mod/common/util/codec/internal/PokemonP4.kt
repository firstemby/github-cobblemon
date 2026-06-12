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
import net.minecraft.resources.ResourceLocation
import java.util.*

internal data class PokemonP4(
    val skillCooldownTicks: Map<ResourceLocation, Int>,
    val originalTrainerFriendship: Optional<Int>,
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        other.skillCooldownsTicks = this.skillCooldownTicks.toMutableMap()
        this.originalTrainerFriendship.ifPresent { other.originalTrainerFriendship = it }
        other.refreshOriginalTrainer()
        return other
    }

    companion object {
        internal val CODEC: MapCodec<PokemonP4> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.unboundedMap(ResourceLocation.CODEC, Codec.INT).optionalFieldOf(DataKeys.POKEMON_SKILL_COOLDOWNS, emptyMap<ResourceLocation, Int>()).forGetter { it.skillCooldownTicks },
                Codec.INT.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER_FRIENDSHIP).forGetter(PokemonP4::originalTrainerFriendship),
            ).apply(instance) {
              skillCooldownTicks,
              originalTrainerFriendship -> PokemonP4(
                skillCooldownTicks,
                originalTrainerFriendship,
              )
            }
        }

        internal fun from(pokemon: Pokemon): PokemonP4 = PokemonP4(
            pokemon.skillCooldownsTicks,
            Optional.ofNullable(pokemon.originalTrainerFriendship),
        )
    }
}
