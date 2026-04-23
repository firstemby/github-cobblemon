/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

/**
 * Reasons for a switch occurring during battle.
 */
enum class SwitchReason {
    VOLUNTARY,
    FAINTED,
    FORCED,
    PIVOT,
    BATON_PASS,
    PIVOT_MOVE,
    TELEPORT,
    ABILITY_FORCED,
    ITEM_FORCED,
    PHAZED
}
