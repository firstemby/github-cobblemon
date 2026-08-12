/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokemon.ai

import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.api.conditional.RegistryLikeCondition
import com.cobblemon.mod.common.api.molang.ObjectValue
import net.minecraft.world.level.block.Block

class ClingBehaviour {
    val canClingToWalls = false
    val canClingToCeilings = false
    val blocks = mutableListOf<RegistryLikeCondition<Block>>()
    val xOffset = 0.0
    val yOffset = 0.0

    @Transient
    val struct = ObjectValue<ClingBehaviour>(this).also {
        it.addFunction("can_cling_to_walls") { DoubleValue(canClingToWalls) }
        it.addFunction("can_cling_to_ceilings") { DoubleValue(canClingToCeilings) }
        it.addFunction("x_offset") { DoubleValue(xOffset) }
        it.addFunction("y_offset") { DoubleValue(yOffset) }
    }
}