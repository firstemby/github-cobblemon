/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.modification

import com.cobblemon.mod.common.CobblemonBlocks
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.ItemInteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class ModFillerBlock(properties: Properties) : Block(properties), SimpleWaterloggedBlock {
    init {
        registerDefaultState(stateDefinition.any()
            .setValue(OFFSET_X, HORIZONTAL_OFFSET_SHIFT)
            .setValue(OFFSET_Y, 0)
            .setValue(OFFSET_Z, HORIZONTAL_OFFSET_SHIFT)
            .setValue(BlockStateProperties.LIT, false)
            .setValue(BlockStateProperties.WATERLOGGED, false))
    }

    override fun codec(): MapCodec<ModFillerBlock> = CODEC

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(OFFSET_X, OFFSET_Y, OFFSET_Z, BlockStateProperties.LIT, BlockStateProperties.WATERLOGGED)
    }

    override fun getRenderShape(state: BlockState): RenderShape = RenderShape.INVISIBLE

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        val anchorPos = anchorPosition(state, pos)
        val anchorState = level.getBlockState(anchorPos)
        if (anchorState.block !is ModificationVariantBlock) {
            return Shapes.empty()
        }
        val variant = ModificationVariantStructures.variantAt(level, anchorPos) ?: return Shapes.block()
        return variant.cellHitbox(ModificationVariantStructures.facingOf(anchorState), cellOffset(state))
    }

    override fun getCollisionShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return getShape(state, level, pos, context)
    }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hit: BlockHitResult): InteractionResult {
        val anchorPos = anchorPosition(state, pos)
        val anchorState = level.getBlockState(anchorPos)
        if (anchorState.block !is ModificationVariantBlock) {
            return InteractionResult.PASS
        }
        return anchorState.useWithoutItem(level, player, hit.withPosition(anchorPos))
    }

    override fun useItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hit: BlockHitResult
    ): ItemInteractionResult {
        val anchorPos = anchorPosition(state, pos)
        val anchorState = level.getBlockState(anchorPos)
        if (anchorState.block !is ModificationVariantBlock) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
        }
        return anchorState.useItemOn(stack, level, player, hand, hit.withPosition(anchorPos))
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack {
        val anchorPos = anchorPosition(state, pos)
        val anchorState = level.getBlockState(anchorPos)
        return if (anchorState.block is ModificationVariantBlock) anchorState.block.getCloneItemStack(level, anchorPos, anchorState) else ItemStack.EMPTY
    }

    override fun playerWillDestroy(level: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        val anchorPos = anchorPosition(state, pos)
        val anchorState = level.getBlockState(anchorPos)
        if (!level.isClientSide && anchorState.block is ModificationVariantBlock) {
            val shouldDrop = !player.isCreative && player.hasCorrectToolForDrops(anchorState)
            level.destroyBlock(anchorPos, shouldDrop, player)
        }
        return super.playerWillDestroy(level, pos, state, player)
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, isMoved: Boolean) {
        if (!newState.`is`(this)) {
            val anchorPos = anchorPosition(state, pos)
            if (level.getBlockState(anchorPos).block is ModificationVariantBlock) {
                level.destroyBlock(anchorPos, true)
            }
        }
        super.onRemove(state, level, pos, newState, isMoved)
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return level.getBlockState(anchorPosition(state, pos)).block is ModificationVariantBlock
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        level: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level))
        }
        return if (canSurvive(state, level, pos)) state else ModificationVariantStructures.replacementFor(state)
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(BlockStateProperties.WATERLOGGED)) Fluids.WATER.getSource(false) else super.getFluidState(state)
    }

    override fun isPathfindable(state: BlockState, type: PathComputationType): Boolean = false

    companion object {
        val CODEC: MapCodec<ModFillerBlock> = simpleCodec(::ModFillerBlock)
        private const val HORIZONTAL_OFFSET_SHIFT = 2
        val OFFSET_X: IntegerProperty = IntegerProperty.create("offset_x", 0, 4)
        val OFFSET_Y: IntegerProperty = IntegerProperty.create("offset_y", 0, 4)
        val OFFSET_Z: IntegerProperty = IntegerProperty.create("offset_z", 0, 4)

        fun stateFor(cell: Vec3i, isLit: Boolean, isWaterlogged: Boolean): BlockState {
            return CobblemonBlocks.MOD_FILLER.defaultBlockState()
                .setValue(OFFSET_X, cell.x + HORIZONTAL_OFFSET_SHIFT)
                .setValue(OFFSET_Y, cell.y)
                .setValue(OFFSET_Z, cell.z + HORIZONTAL_OFFSET_SHIFT)
                .setValue(BlockStateProperties.LIT, isLit)
                .setValue(BlockStateProperties.WATERLOGGED, isWaterlogged)
        }

        fun cellOffset(state: BlockState): Vec3i = Vec3i(
            state.getValue(OFFSET_X) - HORIZONTAL_OFFSET_SHIFT,
            state.getValue(OFFSET_Y),
            state.getValue(OFFSET_Z) - HORIZONTAL_OFFSET_SHIFT
        )

        fun anchorPosition(state: BlockState, pos: BlockPos): BlockPos = pos.subtract(cellOffset(state))
    }
}