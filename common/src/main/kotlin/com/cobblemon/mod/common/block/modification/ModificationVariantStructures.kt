/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.modification

import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.block.entity.ModificationVariantBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Vec3i
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.VoxelShape

object ModificationVariantStructures {
    fun variantAt(level: BlockGetter, pos: BlockPos): ModificationVariant? = (level.getBlockEntity(pos) as? ModificationVariantBlockEntity)?.variant()

    fun isModified(state: BlockState): Boolean = state.hasProperty(ModificationVariantBlock.MODIFIED) && state.getValue(ModificationVariantBlock.MODIFIED)

    fun facingOf(state: BlockState): Direction = if (state.hasProperty(ModificationVariantBlock.FACING)) state.getValue(ModificationVariantBlock.FACING) else Direction.SOUTH

    fun canPlace(context: BlockPlaceContext, variant: ModificationVariant, facing: Direction): Boolean {
        val level = context.level
        return variant.fillerCells(facing).all { cell ->
            val cellPos = context.clickedPos.offset(cell)
            !level.isOutsideBuildHeight(cellPos) && level.getBlockState(cellPos).canBeReplaced(context)
        }
    }

    fun placeFillers(level: LevelAccessor, anchorPos: BlockPos, variant: ModificationVariant, facing: Direction, isLit: Boolean) {
        variant.fillerCells(facing).forEach { cell ->
            val cellPos = anchorPos.offset(cell)
            val isWaterlogged = level.getFluidState(cellPos).type == Fluids.WATER
            level.setBlock(cellPos, ModFillerBlock.stateFor(cell, isLit, isWaterlogged), Block.UPDATE_ALL)
        }
    }

    fun removeFillers(level: LevelAccessor, anchorPos: BlockPos) {
        forEachFiller(level, anchorPos) { fillerPos, fillerState ->
            level.setBlock(fillerPos, replacementFor(fillerState), Block.UPDATE_ALL or Block.UPDATE_SUPPRESS_DROPS)
        }
    }

    fun setFillersLit(level: LevelAccessor, anchorPos: BlockPos, isLit: Boolean) {
        forEachFiller(level, anchorPos) { fillerPos, fillerState ->
            if (fillerState.getValue(BlockStateProperties.LIT) != isLit) {
                level.setBlock(fillerPos, fillerState.setValue(BlockStateProperties.LIT, isLit), Block.UPDATE_ALL)
            }
        }
    }

    fun anchorShape(level: BlockGetter, pos: BlockPos, state: BlockState, fallback: VoxelShape): VoxelShape {
        if (!isModified(state)) {
            return fallback
        }
        val variant = variantAt(level, pos) ?: return fallback
        return variant.cellHitbox(facingOf(state), Vec3i.ZERO)
    }

    fun withVariantComponents(level: BlockGetter, pos: BlockPos, stack: ItemStack): ItemStack {
        val blockEntity = level.getBlockEntity(pos) ?: return stack
        stack.applyComponents(blockEntity.collectComponents())
        return stack
    }

    fun replacementFor(state: BlockState): BlockState {
        val isWaterlogged = state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)
        return if (isWaterlogged) Blocks.WATER.defaultBlockState() else Blocks.AIR.defaultBlockState()
    }

    private fun forEachFiller(level: LevelAccessor, anchorPos: BlockPos, action: (BlockPos, BlockState) -> Unit) {
        val searchMin = anchorPos.offset(ModificationVariant.MIN_CELL_OFFSET)
        val searchMax = anchorPos.offset(ModificationVariant.MAX_CELL_OFFSET)
        BlockPos.betweenClosed(searchMin, searchMax).forEach { searchPos ->
            val state = level.getBlockState(searchPos)
            if (state.block is ModFillerBlock && ModFillerBlock.anchorPosition(state, searchPos) == anchorPos) {
                action(searchPos.immutable(), state)
            }
        }
    }
}