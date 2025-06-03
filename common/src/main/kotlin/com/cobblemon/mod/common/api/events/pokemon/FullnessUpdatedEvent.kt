/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.events.pokemon

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon

/**
 * Event that is fired when a player owned Pokémon has its fullness changed
 */
data class FullnessUpdatedEvent(
        val pokemon: Pokemon,
        val newFullnessInitial: Int
) {
    var newFullness: Int = newFullnessInitial
        set(value) {
            field = value.coerceIn(0, pokemon.getMaxFullness())
        }
}