package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.battles.pokemon.AbstractBattlePokemon
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

class DynamicBattlePokemonProvider : BattlePokemonProvider {
    private val knownPokemon0 = mutableListOf<BattlePokemon>()

    override fun getKnownPokemon(): List<AbstractBattlePokemon> = knownPokemon0

    fun addPokemon(pokemon: BattlePokemon) {
        knownPokemon0.add(pokemon)
    }
}