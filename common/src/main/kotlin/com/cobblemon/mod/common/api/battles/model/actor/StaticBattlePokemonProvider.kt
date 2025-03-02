package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

class StaticBattlePokemonProvider(val pokemon: List<BattlePokemon>) : BattlePokemonProvider{
    override fun getKnownPokemon() = pokemon
}