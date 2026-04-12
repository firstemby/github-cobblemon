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

abstract class WildNestBlock(
    properties: Properties
) : NestBlock(NestVariant.BASIC, properties) {
    abstract val type: WildNestType
    abstract val variantProperty: EnumProperty<WildNestVariant>?

    init {
        var initialState = defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, false)
        variantProperty?.let { property ->
            initialState = initialState.setValue(property, WildNestTypes.defaultVariantFor(type))
        }
        registerDefaultState(initialState)
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        variantProperty?.let { property ->
            builder.add(property)
        }
    }

    override fun getStateForPlacement(ctx: BlockPlaceContext): BlockState? {
        val baseState = ctx.level.getBlockState(ctx.clickedPos.below())
        val fluidState = ctx.level.getFluidState(ctx.clickedPos)
        val placement = WildNestTypes.resolvePlacement(listOf(type), baseState, fluidState) ?: return null

        if (fluidState.type == Fluids.WATER && !placement.waterlogged) {
            return null
        }

        var state = defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, placement.waterlogged)
        variantProperty?.let { property ->
            state = state.setValue(property, placement.variant)
        }
        return state
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

    override fun codec(): MapCodec<NestBlock> = simpleCodec { props -> create(type, props) }

    private fun canBeWaterlogged(): Boolean = type == WildNestType.WATERY || type == WildNestType.KELP

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
        private val FIERY_VARIANT = variantPropertyFor(WildNestType.FIERY)
        private val GRASSY_VARIANT = variantPropertyFor(WildNestType.GRASSY)
        private val LEAFY_VARIANT = variantPropertyFor(WildNestType.LEAFY)
        private val ICY_VARIANT = variantPropertyFor(WildNestType.ICY)
        private val ROCKY_VARIANT = variantPropertyFor(WildNestType.ROCKY)
        private val SANDY_VARIANT = variantPropertyFor(WildNestType.SANDY)
        private val SHROOMY_VARIANT = variantPropertyFor(WildNestType.SHROOMY)
        private val TWIGGY_VARIANT = variantPropertyFor(WildNestType.TWIGGY)
        private val WATERY_VARIANT = variantPropertyFor(WildNestType.WATERY)

        fun create(type: WildNestType, properties: Properties): WildNestBlock = when (type) {
            WildNestType.FIERY -> FieryWildNestBlock(properties)
            WildNestType.GRASSY -> GrassyWildNestBlock(properties)
            WildNestType.LEAFY -> LeafyWildNestBlock(properties)
            WildNestType.ICY -> IcyWildNestBlock(properties)
            WildNestType.KELP -> KelpWildNestBlock(properties)
            WildNestType.PLAYER -> PlayerWildNestBlock(properties)
            WildNestType.ROCKY -> RockyWildNestBlock(properties)
            WildNestType.SANDY -> SandyWildNestBlock(properties)
            WildNestType.SHROOMY -> ShroomyWildNestBlock(properties)
            WildNestType.TWIGGY -> TwiggyWildNestBlock(properties)
            WildNestType.WATERY -> WateryWildNestBlock(properties)
        }

        private fun variantPropertyFor(type: WildNestType): EnumProperty<WildNestVariant> {
            return EnumProperty.create("variant", WildNestVariant::class.java, allowedVariantsFor(type))
        }

        private fun allowedVariantsFor(type: WildNestType): List<WildNestVariant> =
            WildNestVariant.entries.filter { it.serializedName.startsWith("${type.id}_") }
    }

    private class FieryWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.FIERY
        override val variantProperty: EnumProperty<WildNestVariant> get() = FIERY_VARIANT
    }

    private class GrassyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.GRASSY
        override val variantProperty: EnumProperty<WildNestVariant> get() = GRASSY_VARIANT
    }

    private class LeafyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.LEAFY
        override val variantProperty: EnumProperty<WildNestVariant> get() = LEAFY_VARIANT
    }

    private class IcyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.ICY
        override val variantProperty: EnumProperty<WildNestVariant> get() = ICY_VARIANT
    }

    private class KelpWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.KELP
        override val variantProperty: EnumProperty<WildNestVariant>? get() = null
    }

    private class PlayerWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.PLAYER
        override val variantProperty: EnumProperty<WildNestVariant>? get() = null
    }

    private class RockyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.ROCKY
        override val variantProperty: EnumProperty<WildNestVariant> get() = ROCKY_VARIANT
    }

    private class SandyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.SANDY
        override val variantProperty: EnumProperty<WildNestVariant> get() = SANDY_VARIANT
    }

    private class ShroomyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.SHROOMY
        override val variantProperty: EnumProperty<WildNestVariant> get() = SHROOMY_VARIANT
    }

    private class TwiggyWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.TWIGGY
        override val variantProperty: EnumProperty<WildNestVariant> get() = TWIGGY_VARIANT
    }

    private class WateryWildNestBlock(properties: Properties) : WildNestBlock(properties) {
        override val type: WildNestType get() = WildNestType.WATERY
        override val variantProperty: EnumProperty<WildNestVariant> get() = WATERY_VARIANT
    }
}
