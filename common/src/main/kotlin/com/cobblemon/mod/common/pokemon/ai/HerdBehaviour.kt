/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.molang.ObjectValue
import net.minecraft.resources.ResourceLocation

class HerdBehaviour {
    val toleratedLeaders = mutableListOf<ResourceLocation>()
    val maxSize = 6

    @Transient
    val struct = ObjectValue(this).also {
        it.addFunction("has_tolerated_leaders") { DoubleValue(toleratedLeaders.isNotEmpty()) }
        it.addFunction("max_size") { DoubleValue(maxSize) }
    }
}