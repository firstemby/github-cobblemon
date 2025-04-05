/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.actor

import com.cobblemon.mod.common.api.battles.model.actor.ActorType
import com.cobblemon.mod.common.api.battles.model.actor.BattleActor
import com.cobblemon.mod.common.api.battles.model.actor.StaticBattlePokemonProvider
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import java.util.UUID

open class MultiPokemonBattleActor(
    uuid: UUID = UUID.randomUUID(),
    pokemonList: List<BattlePokemon>
) : BattleActor(uuid, StaticBattlePokemonProvider(pokemonList)) {
    override fun getName(): MutableComponent = "Wild Pokémon".text() // TODO probably remove by making it nullable
    override fun nameOwned(name: String): MutableComponent = Component.literal(name)
    override val type = ActorType.WILD
}