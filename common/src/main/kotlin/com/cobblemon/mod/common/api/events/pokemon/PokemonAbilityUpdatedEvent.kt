package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.api.abilities.Ability
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Event that is fired when a player owned Pokémon's ability is changed.
 *
 * @author Waterpicker
 * @since 2024-12-18
 */
data class PokemonAbilityUpdatedEvent(
    val pokemon: Pokemon,
    var ability: Ability
)
