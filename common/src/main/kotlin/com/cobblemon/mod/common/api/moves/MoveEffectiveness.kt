/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.moves

enum class MoveEffectiveness(val showdownId: Int, val multiplier: Double) {
    NORMAL(0, 1.0),
    WEAKNESS(1, 2.0),
    RESISTANCE(2, 0.5),
    IMMUNITY(3, 0.0);

    companion object {
        fun of(showdownId: Int): MoveEffectiveness {
            return entries.find { it.showdownId == showdownId }
                ?: throw IllegalArgumentException("Unknown MoveEffectiveness: $showdownId")
        }
    }
}