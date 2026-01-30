/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.api.nest.WildNestType
import com.cobblemon.mod.common.api.nest.WildNestTypes
import com.cobblemon.mod.common.api.nest.WildNestVariant
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

class WildNestBlock(
    val type: WildNestType,
    properties: Properties
) : NestBlock(NestVariant.BASIC, properties) {

    init {
        registerDefaultState(
            defaultBlockState()
                .setValue(VARIANT, WildNestTypes.defaultVariantFor(type))
                .setValue(BlockStateProperties.WATERLOGGED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(VARIANT)
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val baseState = ctx.level.getBlockState(ctx.clickedPos.below())
        val fluidState = ctx.level.getFluidState(ctx.clickedPos)
        val placement = WildNestTypes.resolvePlacement(listOf(type), baseState, fluidState) ?: return null

        if (fluidState.type == Fluids.WATER && !placement.waterlogged) {
            return null
        }

        return defaultBlockState()
            .setValue(VARIANT, placement.variant)
            .setValue(BlockStateProperties.WATERLOGGED, placement.waterlogged)
    }

    override fun updateShape(state: BlockState, direction: Direction, neighborState: BlockState, level: LevelAccessor, pos: BlockPos, neighborPos: BlockPos): BlockState {
        checkAndBreakIfSubmerged(state, level, pos)
        return state
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, isMoving: Boolean) {
        checkAndBreakIfSubmerged(state, level, pos)
    }

    override fun neighborChanged(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        block: Block,
        fromPos: BlockPos,
        isMoving: Boolean
    ) {
        val blockBelowPos = pos.below()
        if (fromPos == blockBelowPos && level.getBlockState(blockBelowPos).isAir) {
            level.destroyBlock(pos, false)
        }
        checkAndBreakIfSubmerged(state, level, pos)
    }

    override fun canPlaceLiquid(
        player: net.minecraft.world.entity.player.Player?,
        level: BlockGetter,
        pos: BlockPos,
        state: BlockState,
        fluid: Fluid
    ): Boolean {
        return canBeWaterlogged() && !state.getValue(BlockStateProperties.WATERLOGGED) && fluid == Fluids.WATER
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(BlockStateProperties.WATERLOGGED)) {
            Fluids.WATER.getSource(false)
        } else super.getFluidState(state)
    }

    override fun codec(): MapCodec<NestBlock> = simpleCodec { props -> WildNestBlock(type, props) }

    private fun canBeWaterlogged(): Boolean = type == WildNestType.WATERY || type == WildNestType.PLAYER

    private fun checkAndBreakIfSubmerged(state: BlockState, levelAccessor: LevelAccessor, pos: BlockPos) {
        val fluidState = levelAccessor.getFluidState(pos)
        val submerged = (fluidState.type == Fluids.WATER || fluidState.isSource)
        if (submerged && (!canBeWaterlogged() || !state.getValue(BlockStateProperties.WATERLOGGED))) {
            if (levelAccessor is Level) {
                levelAccessor.destroyBlock(pos, true)
            }
        }
    }

    companion object {
        val VARIANT: EnumProperty<WildNestVariant> = EnumProperty.create("variant", WildNestVariant::class.java)
    }
}
