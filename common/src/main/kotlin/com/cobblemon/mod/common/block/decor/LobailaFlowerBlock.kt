/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import net.minecraft.core.BlockPos
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape


class LobailaFlowerBlock(properties: Properties?) : TallFlowerBlock(properties), SimpleWaterloggedBlock {

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(WATERLOGGED, false))
    }

    override fun getFluidState(blockState: BlockState): FluidState? {
        return if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState);
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HALF, WATERLOGGED)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return this.defaultBlockState()
            .setValue(
                WATERLOGGED,
                fluidState.type === Fluids.WATER
            )
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = AABB

    companion object {
        private val AABB = Shapes.box(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)
    }

}