/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Survival effects for held items.
 *
 * @property survivesOhko Whether the item lets the holder survive a one-hit KO (Focus Sash).
 * @property consumable Whether the item is consumed when used.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class SurvivalEffect(
    val survivesOhko: Boolean = false,
    val consumable: Boolean = false
) {
    companion object {
        val NONE = SurvivalEffect()
    }
}
