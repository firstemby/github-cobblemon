/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config.effects

data class StatusEffect(
    val immunities: List<String> = emptyList(),
    val curesOnTrigger: List<String>? = null
) {
    companion object {
        val NONE = StatusEffect()
    }
}
