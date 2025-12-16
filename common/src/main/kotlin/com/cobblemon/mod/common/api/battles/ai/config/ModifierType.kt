/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec

/**
 * The type of score modification to apply.
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class ModifierType {
    /** Multiply the current score by the value */
    MULTIPLY,
    /** Add the value to the current score */
    ADD,
    /** Set the score to the value, ignoring the current score */
    SET,
    /** Use a Molang script to compute the final value */
    SCRIPT;

    companion object {
        val CODEC: Codec<ModifierType> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
