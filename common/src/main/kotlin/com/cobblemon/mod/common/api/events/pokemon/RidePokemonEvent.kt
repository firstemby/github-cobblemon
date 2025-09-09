/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.util.server
import com.bedrockk.molang.runtime.value.DoubleValue
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.api.events.Cancelable
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.moLangFunctionMap
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import net.minecraft.server.level.ServerPlayer

/**
 * The base for all events related to riding a Pokemon.
 *
 * @see [Pre]
 * @see [Post]
 */
interface RidePokemonEvent {
    val player: ServerPlayer
    val pokemon: PokemonEntity

    class Pre(
        override val player: ServerPlayer,
        override val pokemon: PokemonEntity
    ) : RidePokemonEvent, Cancelable() {
        val context = mutableMapOf(
            "player" to player.asMoLangValue(),
            "pokemon" to pokemon.struct
        )
        val functions = moLangFunctionMap(
            cancelFunc
        )
    }

    class Post(
        override val player: ServerPlayer,
        override val pokemon: PokemonEntity
    ) : RidePokemonEvent {
        val context = mutableMapOf(
            "player" to player.asMoLangValue(),
            "pokemon" to pokemon.struct
        )
    }
}