package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.api.types.tera.TeraType
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Event that is fired when a player owned Pokémon has its teratype changed
 *
 * @author Waterpicker
 * @since 2024-12-18
 */
data class TeraTypeUpdatedEvent(
    val pokemon: Pokemon,
    var teraType: TeraType
)