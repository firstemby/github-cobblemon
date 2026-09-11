/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.modification

import com.cobblemon.mod.common.api.modification.ModificationVariant
import com.cobblemon.mod.common.api.modification.ModificationVariants
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

abstract class ModificationVariantBlock(properties: Properties) : BaseEntityBlock(properties) {
    protected abstract fun stockShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape

    protected abstract fun stockPlacementState(context: BlockPlaceContext, variant: ModificationVariant?): BlockState?

    protected abstract fun addStockProperties(builder: StateDefinition.Builder<Block, BlockState>)

    protected open fun onStockPlaced(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {}

    protected open fun anchorPosition(state: BlockState, pos: BlockPos): BlockPos = pos

    protected fun unmodifiedState(): BlockState = stateDefinition.any().setValue(MODIFIED, false)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(MODIFIED)
        addStockProperties(builder)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val variant = ModificationVariants.fromStack(context.itemInHand)
        val stockState = stockPlacementState(context, variant) ?: return null
        if (variant != null && !ModificationVariantStructures.canPlace(context, variant, ModificationVariantStructures.facingOf(stockState))) {
            return null
        }
        return stockState.setValue(MODIFIED, variant != null)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val variant = ModificationVariants.fromStack(stack)
        if (variant != null && ModificationVariantStructures.isModified(state)) {
            ModificationVariantStructures.placeFillers(level, pos, variant, ModificationVariantStructures.facingOf(state), isLit = false)
            return
        }
        onStockPlaced(level, pos, state, placer, stack)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return ModificationVariantStructures.anchorShape(level, pos, state, stockShape(state, level, pos, context))
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack {
        return ModificationVariantStructures.withVariantComponents(level, anchorPosition(state, pos), super.getCloneItemStack(level, pos, state))
    }

    override fun onRemove(state: BlockState, level: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block) && ModificationVariantStructures.isModified(state)) {
            ModificationVariantStructures.removeFillers(level, pos)
        }
        super.onRemove(state, level, pos, newState, moved)
    }

    companion object {
        val MODIFIED: BooleanProperty = BooleanProperty.create("modified")
        val FACING: DirectionProperty = HorizontalDirectionalBlock.FACING
    }
}
