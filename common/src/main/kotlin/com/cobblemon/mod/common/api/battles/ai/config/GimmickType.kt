/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battles.ai.config

import com.mojang.serialization.Codec

enum class GimmickType {
    MEGA,
    DYNAMAX,
    TERA,
    ZMOVE;

    fun toGimmickId(): String = when (this) {
        MEGA -> "mega"
        DYNAMAX -> "max"
        TERA -> "terastal"
        ZMOVE -> "zmove"
    }

    companion object {
        val CODEC: Codec<GimmickType> = Codec.STRING.xmap(
            { name -> valueOf(name.uppercase()) },
            { type -> type.name.lowercase() }
        )
    }
}
