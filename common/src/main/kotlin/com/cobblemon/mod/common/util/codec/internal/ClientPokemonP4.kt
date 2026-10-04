/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util.codec.internal

import com.cobblemon.mod.common.api.mark.Marks
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.client.settings.ServerSettings
import com.cobblemon.mod.common.pokemon.OriginalTrainerType
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.DataKeys
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import java.util.*

internal data class ClientPokemonP4(
    val originalTrainerFriendship: Optional<Int>
) : Partial<Pokemon> {

    override fun into(other: Pokemon): Pokemon {
        this.originalTrainerFriendship.ifPresent { other.originalTrainerFriendship = it }
        return other
    }

    companion object {
        /**
         * do not use cobblemon.config in here, as this is used by the client whose config is different to server, always use [ServerSettings]
         */
        internal val CODEC: MapCodec<ClientPokemonP4> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.INT.optionalFieldOf(DataKeys.POKEMON_ORIGINAL_TRAINER_FRIENDSHIP).forGetter(ClientPokemonP4::originalTrainerFriendship)
                ).apply(instance, ::ClientPokemonP4)
        }

        internal fun from(pokemon: Pokemon): ClientPokemonP4 = ClientPokemonP4(
            Optional.ofNullable(pokemon.originalTrainerFriendship)
        )
    }
}
