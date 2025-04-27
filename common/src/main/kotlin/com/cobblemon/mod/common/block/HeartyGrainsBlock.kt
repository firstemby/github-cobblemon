/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.util.RandomSource
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
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
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

@Suppress("OVERRIDE_DEPRECATION")
class HeartyGrainsBlock(settings: Properties) : CropBlock(settings), SimpleWaterloggedBlock {

    override fun getAgeProperty(): IntegerProperty = AGE

    override fun getMaxAge(): Int = MATURE_AGE

    private val ageAfterHarvest = 3

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(AGE, PART, WATERLOGGED)
    }

    override fun getBaseSeedId(): ItemLike = CobblemonItems.Hearty_Grains

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return AGE_TO_SHAPE[this.getAge(state)]
    }

    override fun getFluidState(state: BlockState): FluidState {
        return if (state.getValue(WATERLOGGED)) Fluids.WATER.defaultFluidState() else Fluids.EMPTY.defaultFluidState()
    }

    override fun randomTick(state: BlockState, world: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (state.getValue(PART) == Part.UPPER || this.isMaxAge(state) || !canGrow(world, pos)) return

        val lightLevel = world.getMaxLocalRawBrightness(pos)
        if (lightLevel >= 9 && random.nextInt(7) == 0) {
            val newAge = state.getValue(AGE) + 1
            world.setBlock(pos, state.setValue(AGE, newAge), UPDATE_CLIENTS)

            if (newAge == MATURE_AGE) {
                val abovePos = pos.above()
                if (world.getBlockState(abovePos).isAir) {
                    world.setBlock(
                        abovePos,
                        this.defaultBlockState().setValue(AGE, newAge).setValue(PART, Part.UPPER),
                        UPDATE_CLIENTS
                    )
                }
            }
        }
    }

    private fun canGrow(world: LevelReader, pos: BlockPos): Boolean {
        return canSurvive(defaultBlockState().setValue(PART, Part.LOWER), world, pos)
    }

    override fun playerWillDestroy(world: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (state.getValue(PART) == Part.UPPER && state.block is HeartyGrainsBlock) {
            val belowPos = pos.below()
            val belowState = world.getBlockState(belowPos)
            world.destroyBlock(pos, true) // Break the lower block without drops
            world.setBlock(
                belowPos,
                belowState.setValue(AGE, ageAfterHarvest), // Reset age to simulate regrowth preparation
                UPDATE_CLIENTS
            )
        } else if (state.getValue(PART) == Part.LOWER) {
            val abovePos = pos.above()
            val aboveState = world.getBlockState(abovePos)
            if (aboveState.block is HeartyGrainsBlock && aboveState.getValue(PART) == Part.UPPER) {
                world.destroyBlock(abovePos, true) // Break the upper block without drops
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
        return if (!canSurvive(state, world, pos)) Blocks.AIR.defaultBlockState() else super.updateShape(
            state,
            direction,
            neighborState,
            world,
            pos,
            neighborPos
        )
    }

    override fun isValidBonemealTarget(world: LevelReader, pos: BlockPos, state: BlockState): Boolean {
        return !this.isMaxAge(state) && state.getValue(PART) == Part.LOWER
    }

    override fun growCrops(world: Level, pos: BlockPos, state: BlockState) {
        if (!canGrow(world, pos)) return

        val newAge = (this.getAge(state) + this.getBonemealAgeIncrease(world)).coerceAtMost(this.maxAge)
        world.setBlock(pos, state.setValue(this.ageProperty, newAge), UPDATE_CLIENTS)

        if (newAge == MATURE_AGE && state.getValue(PART) == Part.LOWER) {
            val abovePos = pos.above()
            if (world.getBlockState(abovePos).isAir) {
                world.setBlock(
                    abovePos,
                    this.defaultBlockState().setValue(AGE, newAge).setValue(PART, Part.UPPER),
                    UPDATE_CLIENTS
                )
            }
        }
    }

    override fun getBonemealAgeIncrease(world: Level): Int = 1

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        return when (state.getValue(PART)) {
            Part.LOWER -> {
                val floor = world.getBlockState(pos.below())
                val fluidState = world.getFluidState(pos)

                // Check if bottom part is submerged in water
                val isSubmergedInWater = fluidState.`is`(FluidTags.WATER) && fluidState.isSource

                // Check for adjacency to water
                val isNearWater = BlockPos.betweenClosed(pos.offset(-1, 0, -1), pos.offset(1, 0, 1))
                    .any { world.getFluidState(it).`is`(FluidTags.WATER) }

                // Check waterlogging state
                val isWaterlogged = state.getValue(WATERLOGGED)

                // Check for valid soil or survival conditions
                isSubmergedInWater || isWaterlogged || isNearWater || floor.`is`(CobblemonBlockTags.HEARTY_GRAINS_PLANTABLE) || floor.`is`(Blocks.MUD) || floor.`is`(Blocks.FARMLAND)
            }
            Part.UPPER -> {
                val below = world.getBlockState(pos.below())
                below.block is HeartyGrainsBlock && below.getValue(PART) == Part.LOWER
                // Upper part survives only if the lower part is valid
            }
        }
    }



    override fun codec(): MapCodec<out CropBlock> = CODEC

    companion object {
        val CODEC = simpleCodec(::HeartyGrainsBlock)
        const val MATURE_AGE = 4
        val WATERLOGGED = BlockStateProperties.WATERLOGGED
        val AGE: IntegerProperty = BlockStateProperties.AGE_4
        val PART: EnumProperty<Part> = EnumProperty.create("part", Part::class.java)

        val AGE_TO_SHAPE = arrayOf(
            box(2.0, 0.0, 0.0, 16.0, 17.0, 16.0), // Stage 1
            box(2.0, 0.0, 0.0, 16.0, 17.0, 16.0), // Stage 2
            box(2.0, 0.0, 0.0, 16.0, 17.0, 16.0), // Stage 3
            box(2.0, 0.0, 0.0, 16.0, 17.0, 16.0), // Stage 4 (Bottom)
            box(2.0, 0.0, 0.0, 16.0, 17.0, 16.0)  // Stage 5 (Bottom)
        )
    }

    enum class Part : StringRepresentable {
        LOWER, UPPER;

        override fun getSerializedName(): String = name.lowercase()
    }

    init {
        this.registerDefaultState(
            this.stateDefinition.any()
                .setValue(AGE, 0)
                .setValue(PART, Part.LOWER)
                .setValue(WATERLOGGED, false)
        )
    }
}