package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.battles.pokemon.AbstractBattlePokemon

class StaticBattlePokemonProvider(val pokemon: List<AbstractBattlePokemon>) : BattlePokemonProvider{
    override fun getKnownPokemon(): List<AbstractBattlePokemon> = pokemon
}