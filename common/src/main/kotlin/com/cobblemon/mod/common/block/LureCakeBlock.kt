/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.LureCakeBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class LureCakeBlock(settings: Properties): CakeBlock(settings) {
    override fun codec(): MapCodec<LureCakeBlock> {
        return simpleCodec(::LureCakeBlock)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return LureCakeBlockEntity(pos, state)
    }
}
