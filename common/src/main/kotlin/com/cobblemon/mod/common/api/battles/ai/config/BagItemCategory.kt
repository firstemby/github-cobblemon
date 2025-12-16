/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

/**
 * Categories of items that can be used during battle from a trainer's bag.
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class BagItemCategory {
    /** Healing items like Potions, Super Potions, Full Restore */
    HEALING,
    /** Status curing items like Antidote, Awakening, Full Heal */
    STATUS_CURE,
    /** PP restoration items like Ether, Elixir */
    PP_RESTORE,
    /** Revival items like Revive, Max Revive */
    REVIVE,
    /** Stat boosting items like X Attack, X Defense */
    STAT_BOOST,
    /** Guard items like Guard Spec, Dire Hit */
    GUARD,
    /** Items that don't fit other categories */
    OTHER
}
