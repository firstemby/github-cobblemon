/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.riding.util

data class RideStatOverrideData(
    var min: Double,
    var max: Double,
    var tier: RidingTier,
    var boost: Double) {

    fun getTierBoundBoost(): Double {
        return (((tier.tierRange.last - tier.tierRange.first) / 100) * boost) + tier.tierRange.first
    }

    fun getCurrStatValue(): Double {
        return (((max - min) / 100) * getTierBoundBoost()) + min
    }
}
