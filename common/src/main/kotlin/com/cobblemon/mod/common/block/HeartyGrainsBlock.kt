/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.ItemLike
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.CropBlock
import net.minecraft.world.level.block.SimpleWaterloggedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class HeartyGrainsBlock(settings: Properties) : CropBlock(settings), SimpleWaterloggedBlock {
    companion object {
        val CODEC = simpleCodec(::HeartyGrainsBlock)
        const val MATURE_AGE = 6
        const val AGE_AFTER_HARVEST = 3 // Last single block stage
        val WATERLOGGED = BlockStateProperties.WATERLOGGED
        val AGE: IntegerProperty = IntegerProperty.create("age", 0, MATURE_AGE)
        val HALF: EnumProperty<DoubleBlockHalf> = BlockStateProperties.DOUBLE_BLOCK_HALF

        val AGE_TO_SHAPE = arrayOf(
            box(0.0, 0.0, 0.0, 16.0, 3.0, 16.0), // Stage 0
            box(0.0, 0.0, 0.0, 16.0, 5.0, 16.0), // Stage 1
            box(0.0, 0.0, 0.0, 16.0, 10.0, 16.0), // Stage 2
            box(0.0, 0.0, 0.0, 16.0, 14.0, 16.0), // Stage 3
            box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),  // Stage 4
            box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),  // Stage 5
            box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0),  // Stage 6
        )

        val AGE_TO_SHAPE_TOP = arrayOf(
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0), // Stage 0
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0), // Stage 1
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0), // Stage 2
            box(1.0, 0.0, 1.0, 15.0, 1.0, 15.0), // Stage 3
            box(0.0, 0.0, 0.0, 16.0, 4.0, 16.0),  // Stage 4
            box(0.0, 0.0, 0.0, 16.0, 11.0, 16.0),  // Stage 5
            box(0.0, 0.0, 0.0, 16.0, 15.0, 16.0),  // Stage 6
        )
    }

    override fun codec(): MapCodec<out CropBlock> = CODEC

    init {
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(HALF, DoubleBlockHalf.LOWER)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun getAgeProperty(): IntegerProperty = AGE

    override fun getMaxAge(): Int = MATURE_AGE

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE, HALF, WATERLOGGED)
    }

    override fun getBaseSeedId(): ItemLike = CobblemonItems.HEARTY_GRAINS

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER) AGE_TO_SHAPE_TOP[getAge(state)] else AGE_TO_SHAPE[this.getAge(state)]

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.defaultFluidState() else Fluids.EMPTY.defaultFluidState()
    }

    override fun randomTick(state: BlockState, world: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (state.getValue(HALF) == DoubleBlockHalf.UPPER || !canGrow(world, pos, state)) return

        if (hasSufficientLight(world, pos) && random.nextInt(7) == 0) {
            grow(world, pos, state, 1)
        }
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val fluidState = context.level.getFluidState(context.clickedPos)
        return this.defaultBlockState()
            .setValue(
            WATERLOGGED,
            fluidState.type === Fluids.WATER
        )
    }

    private fun canGrow(world: LevelReader, pos: BlockPos, state: BlockState): Boolean {
        return !this.isMaxAge(state) && canSurvive(state, world, pos)
    }

    private fun canGrowInto(world: LevelReader, pos: BlockPos): Boolean {
        val blockState = world.getBlockState(pos)
        return blockState.isAir || blockState.`is`(CobblemonBlocks.HEARTY_GRAINS)
    }

    override fun playerWillDestroy(world: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!world.isClientSide) {
            if (state.getValue(HALF) == DoubleBlockHalf.UPPER) {
                val belowPos = pos.below()
                val belowState = world.getBlockState(belowPos)
                world.destroyBlock(pos, true) // Break the lower block without drops
                world.setBlock(
                    belowPos,
                    belowState.setValue(AGE, AGE_AFTER_HARVEST), // Reset age to simulate regrowth preparation
                    UPDATE_CLIENTS
                )
            } else if (state.getValue(HALF) == DoubleBlockHalf.LOWER) {
                val abovePos = pos.above()
                val aboveState = world.getBlockState(abovePos)
                if (aboveState.block is HeartyGrainsBlock && aboveState.getValue(HALF) == DoubleBlockHalf.UPPER) {
                    world.destroyBlock(abovePos, true) // Break the upper block without drops
                }
            }
        }
        return super.playerWillDestroy(world, pos, state, player)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        if (state.getValue(WATERLOGGED)) {
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world))
        }
        val doubleBlockHalf = state.getValue(HALF)
        return if (direction.axis !== Direction.Axis.Y || doubleBlockHalf == DoubleBlockHalf.LOWER != (direction == Direction.UP) ||
            neighborState.`is`(this) && neighborState.getValue(HALF) != doubleBlockHalf) {
            if (doubleBlockHalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(world, pos))
                Blocks.AIR.defaultBlockState()
            else super.updateShape(state, direction, neighborState, world, pos, neighborPos)
        } else {
            Blocks.AIR.defaultBlockState()
        }
    }

    override fun isValidBonemealTarget(level: LevelReader, pos: BlockPos, state: BlockState): Boolean {
        val posAndState = getLowerHalf(level, pos, state)
        return if (posAndState == null) false else this.canGrow(
            level,
            posAndState.first,
            posAndState.second
        )
    }

    override fun performBonemeal(level: ServerLevel, random: RandomSource, pos: BlockPos, state: BlockState) {
        val posAndState = getLowerHalf(level, pos, state)
        if (posAndState != null) {
            grow(level, posAndState.first, posAndState.second, getBonemealAgeIncrease(level))
        }
    }

    fun grow(world: Level, pos: BlockPos, state: BlockState, increment: Int) {
        if (!canGrow(world, pos, state)) return

        val newAge = (this.getAge(state) + increment).coerceAtMost(this.maxAge)
        world.setBlock(pos, state.setValue(this.ageProperty, newAge), UPDATE_CLIENTS)

        // Set top half depending on age
        if (newAge >= (AGE_AFTER_HARVEST + 1)) {
            val abovePos = pos.above()
            val stateAbove = world.getBlockState(abovePos)
            if (stateAbove.isAir) {
                world.setBlock(
                    abovePos,
                    this.defaultBlockState().setValue(AGE, newAge).setValue(HALF, DoubleBlockHalf.UPPER),
                    UPDATE_CLIENTS
                )
            }
            else if (stateAbove.getValue(HALF) == DoubleBlockHalf.UPPER) {
                world.setBlock(
                    abovePos,
                    stateAbove.setValue(AGE, newAge),
                    UPDATE_CLIENTS
                )
            }
        }
    }

    override fun getBonemealAgeIncrease(world: Level): Int = 1

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        return when (state.getValue(HALF)) {
            DoubleBlockHalf.LOWER -> {
                val floorBlock = pos.below()
                val floor = world.getBlockState(floorBlock)

                // Check if bottom part is submerged in water
                val fluidState = world.getFluidState(pos)
                if (fluidState.`is`(FluidTags.WATER) && fluidState.isSource)
                    return floor.`is`(CobblemonBlockTags.HEARTY_GRAINS_WATER_PLANTABLE) && canGrowInto(world, pos.above())

                floor.`is`(CobblemonBlockTags.HEARTY_GRAINS_LAND_PLANTABLE) && canGrowInto(world, pos.above())
            }
            DoubleBlockHalf.UPPER -> {
                getLowerHalf(world, pos, state) != null
                // Upper part survives only if the lower part is valid
            }
        }
    }

    private fun getLowerHalf(level: LevelReader, pos: BlockPos, state: BlockState): Pair<BlockPos, BlockState>? {
        if (isLower(state)) {
            return Pair(pos, state)
        } else {
            val blockPos = pos.below()
            val blockState = level.getBlockState(blockPos)
            return if (isLower(blockState)) Pair(
                blockPos,
                blockState
            ) else null
        }
    }

    private fun isLower(state: BlockState): Boolean =
        state.`is`(CobblemonBlocks.HEARTY_GRAINS) && state.getValue(HALF) == DoubleBlockHalf.LOWER
}