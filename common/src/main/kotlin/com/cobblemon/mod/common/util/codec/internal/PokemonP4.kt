/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.codec.internal

import com.cobblemon.mod.common.api.pokemon.transformation.progress.TransformationProgress
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.pokemon.transformation.controller.ServerFormController
import com.cobblemon.mod.common.pokemon.transformation.form.TemporaryForm
import com.cobblemon.mod.common.util.DataKeys
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

internal data class PokemonP4(
    val originalTrainerFriendship: Optional<Int>,
    val formIds: List<String>,
    val requirementProgress: List<TransformationProgress<*>>,
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        this.originalTrainerFriendship.ifPresent { other.originalTrainerFriendship = it }
        other.refreshOriginalTrainer()
        other.formProxy.overrideController(ServerFormController(other, formIds.toSet()))
        other.requirementProgress.clear()
        other.requirementProgress.addAll(requirementProgress)
        return other
    }

    companion object {
        internal val CODEC: MapCodec<PokemonP4> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.INT.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER_FRIENDSHIP).forGetter(PokemonP4::originalTrainerFriendship),
                Codec.STRING.listOf().fieldOf(DataKeys.POKEMON_FORMS).forGetter(PokemonP4::formIds),
                TransformationProgress.codec().listOf().fieldOf(DataKeys.POKEMON_PROGRESS).forGetter(PokemonP4::requirementProgress)
            ).apply(instance) {
              originalTrainerFriendship, formController, progress -> PokemonP4(
                originalTrainerFriendship,
                formController,
                progress
              )
            }
        }

        internal fun from(pokemon: Pokemon): PokemonP4 = PokemonP4(
            Optional.ofNullable(pokemon.originalTrainerFriendship),
            pokemon.formProxy.server().map(TemporaryForm::id),
            pokemon.requirementProgress.toList()
        )
    }
}
