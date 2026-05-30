/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.codec.internal

import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerFormController
import com.cobblemon.mod.common.util.DataKeys
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

internal data class PokemonP4(
    val originalTrainerFriendship: Optional<Int>,
    val formController: Optional<ServerFormController.Intermediate>,
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        this.originalTrainerFriendship.ifPresent { other.originalTrainerFriendship = it }
        other.refreshOriginalTrainer()
        this.formController.ifPresent {
            other.formProxy.overrideController(it.create(other))
        }
        return other
    }

    companion object {
        internal val CODEC: MapCodec<PokemonP4> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.INT.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER_FRIENDSHIP).forGetter(PokemonP4::originalTrainerFriendship),
                ServerFormController.CODEC.optionalFieldOf(DataKeys.POKEMON_FORMS).forGetter(PokemonP4::formController)
            ).apply(instance) {
              originalTrainerFriendship, formController -> PokemonP4(
                originalTrainerFriendship,
                formController
              )
            }
        }

        internal fun from(pokemon: Pokemon): PokemonP4 = PokemonP4(
            Optional.ofNullable(pokemon.originalTrainerFriendship),
            Optional.ofNullable((pokemon.formProxy.current() as? ServerFormController)?.asIntermediate()),
        )
    }
}
