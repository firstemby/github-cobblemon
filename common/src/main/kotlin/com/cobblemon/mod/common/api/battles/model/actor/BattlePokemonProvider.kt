package com.cobblemon.mod.common.api.battles.model.actor

import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

/**
 * Allows for a [BattleActor] to access Pokemon it controls.
 *
 * We don't always know what Pokemon an actor has when starting a battle
 */
interface BattlePokemonProvider {
    fun getKnownPokemon(): List<BattlePokemon>;
}