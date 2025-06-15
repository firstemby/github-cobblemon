/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.general

import com.mojang.serialization.MapCodec
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.AmethystClusterBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Fluids


class SixDirectionalBlock(properties: Properties) : DirectionalBlock(properties) {
    init {registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN))}

    companion object {
        val CODEC = simpleCodec(::SixDirectionalBlock)}

    public override fun codec(): MapCodec<SixDirectionalBlock> {
        return CODEC
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {builder.add(FACING)}

    @Deprecated("Deprecated in Java")
    override fun rotate(state: BlockState, rotation: Rotation): BlockState {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    }

/*    override fun mirror(state: BlockState, mirror: Mirror): BlockState {
        return state.rotate(mirror.getRotation((state.getValue(FACING))!!))
    }*/

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        return defaultBlockState().setValue(FACING, context.clickedFace)
    }
}
