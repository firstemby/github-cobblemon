/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.model

/**
 * How a [PokemonBattle] came to an end.
 */
enum class BattleEndResult {
    /** One side defeated the other. */
    VICTORY,
    /** A wild Pokémon was captured, ending the battle. */
    CAPTURE,
    /** An actor forfeited, giving the victory to the other side. */
    FORFEIT,
    /** A player fled from a wild Pokémon. */
    FLED,
    /** The battle was stopped without a winner, such as by a command, a player disconnecting or an error. */
    STOPPED
}
