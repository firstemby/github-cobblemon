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
 * Types of battle gimmicks (special mechanics) that can be used.
 *
 * @author Amo Aster
 * @since December 2025
 */
enum class GimmickType {
    /** Mega Evolution */
    MEGA,
    /** Dynamax/Gigantamax */
    DYNAMAX,
    /** Terastallization */
    TERA,
    /** Z-Moves */
    ZMOVE;

    companion object {
        val CODEC: Codec<GimmickType> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
