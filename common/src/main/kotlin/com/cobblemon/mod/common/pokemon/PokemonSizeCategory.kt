/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon

import com.cobblemon.mod.common.Cobblemon

enum class PokemonSizeCategory {
    XS,
    S,
    M,
    L,
    XL;
    
    companion object {
        // Find which size category corresponds to the given scale modifier.
        // The scale modifier should be between the intrinsic min and max size.
        fun fromScale(scaleModifier: Float): PokemonSizeCategory {
            val config = Cobblemon.config
            val minScale = config.pokemonIntrinsicSizeMin
            val maxScale = config.pokemonIntrinsicSizeMax
            val range = (maxScale - minScale).coerceAtLeast(0.0001F)
            val segmentSize = range / PokemonSizeCategory.entries.size
            val adjustedScale = (scaleModifier - minScale).coerceIn(0F, range)
            val index = (adjustedScale / segmentSize).toInt().coerceIn(0, PokemonSizeCategory.entries.size - 1)
            return PokemonSizeCategory.entries[index]
        }

        fun translationKey(category: PokemonSizeCategory) = "cobblemon.ui.size_category.${category.name.lowercase()}"
    }
}
