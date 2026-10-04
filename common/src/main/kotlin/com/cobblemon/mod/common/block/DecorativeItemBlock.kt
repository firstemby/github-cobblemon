/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class DecorativeItemBlock(settings: Properties, val type: DecorativeItemBlockType): HorizontalDirectionalBlock(settings), SimpleWaterloggedBlock {

    enum class DecorativeItemBlockType: StringRepresentable {
        //For basic use, also ignore camelcase errors, it's just dumb
        CENTERED_8x,
        CENTERED_6x,
        FLATTENED_8x6,
        FLATTENED_8x4,
        FLATTENED_10x4,
        //For specific items
        TEACUP,
        TEAPOT,
        KNOT,
        POUCH,
        NEVERMELT,
        SCOPE,
        WIDE,
        ZOOM,
        IZER;

        override fun getSerializedName(): String = this.name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromEnum(::values)
        }
    }

    companion object {
        val CODEC: MapCodec<DecorativeItemBlock> = RecordCodecBuilder.mapCodec { it.group(
            propertiesCodec(),
            DecorativeItemBlockType.CODEC.fieldOf("type").forGetter(DecorativeItemBlock::type)
        ).apply(it, ::DecorativeItemBlock) }

        val CENTERED_8x = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75)
        val CENTERED_6x = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.375, 0.6875)
        val FLAT6_HITBOX = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.375, 0.75)
        val FLAT4_HITBOX = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.25, 0.75)
        val SMOOSHED4_HITBOX = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.25, 0.8125)
        val TEACUP_HITBOX = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.25, 0.6875)
        val TEAPOT_HITBOX = Shapes.box(0.1875, 0.0, 0.1875, 0.8125, 0.625, 0.8125)
        val KNOT_HITBOX = Shapes.box(0.375, 0.0, 0.375, 0.75, 0.375, 0.75)
        val POUCH_HITBOX = Shapes.box(0.3125, 0.0, 0.3125, 0.6875, 0.3125, 0.6875)
        val NEVERMELT_HITBOX = Shapes.box(0.25, 0.0, 0.3125, 0.6875, 0.25, 0.75)
        val SCOPE_HITBOX = Shapes.box(0.625, 0.0, 0.4375, 0.9375, 0.125, 0.75)
        val WIDE_HITBOX = Shapes.box(0.625, 0.0, 0.125, 0.9375, 0.0625, 0.625)
        val ZOOM_HITBOX = Shapes.box(0.125, 0.0, 0.1875, 0.375, 0.1875, 0.5)
        val IZER_HITBOX = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.375, 0.75)

        fun getShape(type: DecorativeItemBlockType): VoxelShape {
            return when (type) {
                DecorativeItemBlockType.TEAPOT -> TEAPOT_HITBOX
                DecorativeItemBlockType.TEACUP -> TEACUP_HITBOX
                DecorativeItemBlockType.KNOT -> KNOT_HITBOX
                DecorativeItemBlockType.POUCH -> POUCH_HITBOX
                DecorativeItemBlockType.NEVERMELT -> NEVERMELT_HITBOX
                DecorativeItemBlockType.SCOPE -> SCOPE_HITBOX
                DecorativeItemBlockType.WIDE -> WIDE_HITBOX
                DecorativeItemBlockType.ZOOM -> ZOOM_HITBOX
                DecorativeItemBlockType.IZER -> IZER_HITBOX
                DecorativeItemBlockType.FLATTENED_10x4 -> SMOOSHED4_HITBOX
                DecorativeItemBlockType.FLATTENED_8x4 -> FLAT4_HITBOX
                DecorativeItemBlockType.FLATTENED_8x6 -> FLAT6_HITBOX
                DecorativeItemBlockType.CENTERED_6x -> CENTERED_6x
                else -> CENTERED_8x
            }
        }
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HORIZONTAL_FACING, Direction.NORTH)
            .setValue(WATERLOGGED, false))
    }

    override fun codec(): MapCodec<DecorativeItemBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HORIZONTAL_FACING, WATERLOGGED)
    }

    override fun getFluidState(blockState: BlockState): FluidState? = if (blockState.getValue(WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(blockState)

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, world: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }
        return if (state.getValue(HORIZONTAL_FACING).opposite == direction && !state.canSurvive(world, pos)) Blocks.AIR.defaultBlockState()
        else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val direction = state.getValue(HORIZONTAL_FACING)
        return rotateShape(Direction.NORTH, direction, getShape(type))
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return super.getStateForPlacement(context)?.setValue(HORIZONTAL_FACING, context.horizontalDirection.opposite)?.setValue(WATERLOGGED, fluidState.type === Fluids.WATER)
    }
}
