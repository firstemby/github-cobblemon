/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.api.pokemon.stats.Stat
import com.cobblemon.mod.common.api.types.tera.TeraType
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Event that is fired when a player owned Pokémon ia gaining ev values for a state.
 *
 * @author Waterpicker
 * @since 2024-12-18
 */
data class EVsGainedEvent(
    val pokemon: Pokemon,
    var stat: Stat,
    var value: Int
)