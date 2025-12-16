/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

/**
 * Contact-triggered effects for held items.
 *
 * @property damagePercent Damage dealt to attacker as decimal (e.g., 0.166 for Rocky Helmet).
 * @property statusEffect Status to inflict on attacker (e.g., "poison").
 * @property statDrops Stats to lower on attacker.
 *
 * @author Amo Aster
 * @since December 2025
 */
data class ContactEffect(
    val damagePercent: Double? = null,
    val statusEffect: String? = null,
    val statDrops: Map<String, Int>? = null
) {
    companion object {
        val NONE = ContactEffect()
    }
}
