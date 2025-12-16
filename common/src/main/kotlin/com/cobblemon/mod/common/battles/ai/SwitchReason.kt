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
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class SwitchReason {
    /** Player chose to switch */
    VOLUNTARY,
    /** Pokemon fainted */
    FAINTED,
    /** Forced by move (Roar, Whirlwind, etc.) */
    FORCED,
    /** U-turn, Volt Switch, etc. */
    PIVOT,
    /** Baton Pass (passes stat boosts) */
    BATON_PASS,
    /** Pivot move (U-turn, Volt Switch, Flip Turn, etc.) */
    PIVOT_MOVE,
    /** Teleport */
    TELEPORT,
    /** Forced by ability (Emergency Exit, Wimp Out) */
    ABILITY_FORCED,
    /** Forced by item (Eject Button, Eject Pack) */
    ITEM_FORCED,
    /** Phazed by Dragon Tail, Circle Throw, Roar, Whirlwind */
    PHAZED
}
