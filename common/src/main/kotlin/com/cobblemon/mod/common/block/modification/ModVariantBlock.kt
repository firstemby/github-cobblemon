/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.modification

import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.block.entity.ModVariantBlockEntity
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ModVariantBlock(properties: Properties) : ModificationVariantBlock(properties) {
    init {
        registerDefaultState(unmodifiedState().setValue(FACING, Direction.SOUTH))
    }

    override fun codec(): MapCodec<ModVariantBlock> = CODEC

    override fun addStockProperties(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun stockPlacementState(context: BlockPlaceContext, variant: ModificationVariant?): BlockState? {
        if (variant == null) {
            return null
        }
        return defaultBlockState().setValue(FACING, context.horizontalDirection)
    }

    override fun stockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = Shapes.block()

    override fun newBlockEntity(pos: BlockPos, state: BlockState) = ModVariantBlockEntity(pos, state)

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState = state.rotate(mirror.getRotation(state.getValue(FACING)))

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false

    companion object {
        val CODEC: MapCodec<ModVariantBlock> = simpleCodec(::ModVariantBlock)
    }
}
