/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * HP-related effects for held items.
 *
 * @property recoveryPerTurn HP recovery per turn as decimal (e.g., 0.0625 for Leftovers = 1/16).
 * @property recoilPercent Recoil damage as decimal (e.g., 0.1 for Life Orb = 10%).
 * @property healPercent Flat heal amount as percent (e.g., 25 for Sitrus Berry).
 * @property triggerAtHpPercent HP threshold to trigger effect (e.g., 50 for Sitrus Berry).
 *
 * @author Amo Aster
 * @since December 2025
 */
data class HpEffect(
    val recoveryPerTurn: Double? = null,
    val recoilPercent: Double? = null,
    val healPercent: Double? = null,
    val triggerAtHpPercent: Double? = null
) {
    companion object {
        val NONE = HpEffect()
    }
}
