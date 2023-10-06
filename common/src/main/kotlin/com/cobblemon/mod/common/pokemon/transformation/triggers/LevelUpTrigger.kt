/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.transformation.triggers

import com.cobblemon.mod.common.api.pokemon.transformation.trigger.PassiveTrigger

/**
 * Represents a [PassiveTrigger].
 *
 * @author Licious
 * @since March 20th, 2022
 */
open class LevelUpTrigger : PassiveTrigger {

    companion object {
        const val ADAPTER_VARIANT = "level_up"
        // Just for user convenience sake as we may have passive evolutions not backed by level ups
        const val ALTERNATIVE_ADAPTER_VARIANT = "passive"
    }
}