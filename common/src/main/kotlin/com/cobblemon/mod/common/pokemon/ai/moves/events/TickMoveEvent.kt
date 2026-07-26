/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai.moves.events

class TickMoveEvent(
    val startTick: Int = 0,
    val intervalTicks: Int = 1, // Must be > 0
    val maxOccurrences: Int = 1,
    val actions: List<MoveEventAction>, // create | move

)