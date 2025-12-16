/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec

/**
 * The type of action an AI can take during a battle turn.
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class ActionType {
    /** Select a move to use */
    MOVE,
    /** Switch to a different Pokémon */
    SWITCH,
    /** Use an item from the bag */
    ITEM,
    /** Activate a gimmick (Mega Evolution, Dynamax, Terastallize, Z-Move) */
    GIMMICK,
    /** Pass/skip the turn (rarely used) */
    PASS;

    companion object {
        val CODEC: Codec<ActionType> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
