/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.CopperFridgeBlockEntity
import com.cobblemon.mod.common.util.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.util.StringRepresentable
import net.minecraft.world.Containers
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.piglin.PiglinAi
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class CopperFridgeBlock(properties: Properties): BaseEntityBlock(properties), SimpleWaterloggedBlock {
    companion object {
        val CODEC = simpleCodec(::CopperFridgeBlock)
        val PART = EnumProperty.create("part", CopperFridgePart::class.java)

        private val NORTH_AABB_TOP = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0625, 1.0, 1.0, 1.0),
            Shapes.box(0.25, 0.125, 0.0, 0.375, 0.3125, 0.0625),
            Shapes.box(0.625, 0.125, 0.0, 0.75, 0.3125, 0.0625)
        )

        private val SOUTH_AABB_TOP = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 1.0, 1.0, 0.9375),
            Shapes.box(0.625, 0.125, 0.9375, 0.75, 0.3125, 1.0),
            Shapes.box(0.25, 0.125, 0.9375, 0.375, 0.3125, 1.0)
        )

        private val WEST_AABB_TOP = Shapes.or(
            Shapes.box(0.0625, 0.0, 0.0, 1.0, 1.0, 1.0),
            Shapes.box(0.0, 0.125, 0.625, 0.0625, 0.3125, 0.75),
            Shapes.box(0.0, 0.125, 0.25, 0.0625, 0.3125, 0.375)
        )

        private val EAST_AABB_TOP = Shapes.or(
            Shapes.box(0.0, 0.0, 0.0, 0.9375, 1.0, 1.0),
            Shapes.box(0.9375, 0.125, 0.25, 1.0, 0.3125, 0.375),
            Shapes.box(0.9375, 0.125, 0.625, 1.0, 0.3125, 0.75)
        )

        private val NORTH_AABB_BOTTOM = Shapes.or(
            Shapes.box(0.0, 0.0625, 0.0625, 1.0, 1.0, 1.0),
            Shapes.box(0.0625, 0.0, 0.125, 0.9375, 0.0625, 0.9375)
        )

        private val SOUTH_AABB_BOTTOM = Shapes.or(
            Shapes.box(0.0, 0.0625, 0.0, 1.0, 1.0, 0.9375),
            Shapes.box(0.0625, 0.0, 0.0625, 0.9375, 0.0625, 0.875)
        )

        private val WEST_AABB_BOTTOM = Shapes.or(
            Shapes.box(0.0625, 0.0625, 0.0, 1.0, 1.0, 1.0),
            Shapes.box(0.125, 0.0, 0.0625, 0.9375, 0.0625, 0.9375)
        )

        private val EAST_AABB_BOTTOM = Shapes.or(
            Shapes.box(0.0, 0.0625, 0.0, 0.9375, 1.0, 1.0),
            Shapes.box(0.0625, 0.0, 0.0625, 0.875, 0.0625, 0.9375)
        )
    }

    enum class CopperFridgePart(private val label: String) : StringRepresentable {
        TOP("top"),
        BOTTOM("bottom");
        override fun getSerializedName(): String = label
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
            .setValue(PART, CopperFridgePart.BOTTOM)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun newBlockEntity(blockPos: BlockPos, blockState: BlockState) = if (blockState.getValue(PART) == CopperFridgePart.BOTTOM) CopperFridgeBlockEntity(blockPos, blockState) else null

    override fun getName(): MutableComponent {
        return Component.translatable("block.cobblemon.copper_fridge")
    }

    override fun getShape(
        blockState: BlockState,
        blockGetter: BlockGetter,
        blockPos: BlockPos,
        collisionContext: CollisionContext
    ): VoxelShape {
        return if (blockState.getValue(PART) == CopperFridgePart.TOP)  {
            when (blockState.getValue(HorizontalDirectionalBlock.FACING)) {
                Direction.SOUTH -> SOUTH_AABB_TOP
                Direction.WEST -> WEST_AABB_TOP
                Direction.EAST -> EAST_AABB_TOP
                else -> NORTH_AABB_TOP
            }
        } else {
            when (blockState.getValue(HorizontalDirectionalBlock.FACING)) {
                Direction.SOUTH -> SOUTH_AABB_BOTTOM
                Direction.WEST -> WEST_AABB_BOTTOM
                Direction.EAST -> EAST_AABB_BOTTOM
                else -> NORTH_AABB_BOTTOM
            }
        }
    }

    fun getPositionOfOtherPart(state: BlockState, pos: BlockPos): BlockPos {
        return if (state.getValue(PART) == CopperFridgePart.BOTTOM) {
            pos.above()
        } else {
            pos.below()
        }
    }

    fun getBasePosition(state: BlockState, pos: BlockPos): BlockPos {
        return if (isBase(state)) {
            pos
        } else {
            pos.below()
        }
    }

    private fun isBase(state: BlockState): Boolean = state.getValue(PART) == CopperFridgePart.BOTTOM

    override fun setPlacedBy(
        world: Level,
        pos: BlockPos,
        state: BlockState,
        placer: LivingEntity?,
        itemStack: ItemStack
    ) {
        world.setBlock(pos.above(),
            state
            .setValue(PART, CopperFridgePart.TOP)
            .setValue(WATERLOGGED, world.getFluidState((pos.above())).type == Fluids.WATER)
            , 3)
        world.blockUpdated(pos, Blocks.AIR)
        state.updateNeighbourShapes(world, pos, 3)
    }

    override fun getStateForPlacement(blockPlaceContext: BlockPlaceContext): BlockState? {
        val abovePosition = blockPlaceContext.clickedPos.above()
        val world = blockPlaceContext.level
        if (world.getBlockState(abovePosition).canBeReplaced(blockPlaceContext) && !world.isOutsideBuildHeight(abovePosition)) {
            return defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, blockPlaceContext.horizontalDirection.opposite)
                .setValue(PART, CopperFridgePart.BOTTOM)
                .setValue(WATERLOGGED, blockPlaceContext.level.getFluidState(blockPlaceContext.clickedPos).type == Fluids.WATER)
        }

        return null
    }

    override fun canSurvive(state: BlockState, world: LevelReader, pos: BlockPos): Boolean {
        val blockPos = pos.below()
        val blockState = world.getBlockState(blockPos)
        return if (state.getValue(PART) == CopperFridgePart.BOTTOM) blockState.isFaceSturdy(world, blockPos, Direction.UP) else blockState.`is`(this)// todo (techdaan): ensure this is the right mapping
    }

    override fun playerWillDestroy(world: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!world.isClientSide && player?.isCreative == true) {
            var blockPos: BlockPos = BlockPos.ZERO
            var blockState: BlockState = state
            val part = state.getValue(PART)
            if (part == CopperFridgePart.TOP && world.getBlockState(pos.below().also { blockPos = it }).also { blockState = it }.`is`(state.block) && blockState.getValue(PART) == CopperFridgePart.BOTTOM) {
                val blockState2 = if (blockState.fluidState.`is`(Fluids.WATER)) Blocks.WATER.defaultBlockState() else Blocks.AIR.defaultBlockState()
                world.setBlock(blockPos, blockState2, UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                world.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, getId(blockState))
            }
        }
        return super.playerWillDestroy(world, pos, state, player)
    }

    override fun codec() = CODEC

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(
        blockState: BlockState,
        pathComputationType: PathComputationType
    ): Boolean = false

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HorizontalDirectionalBlock.FACING)
        builder.add(PART)
        builder.add(WATERLOGGED)
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(blockState: BlockState, rotation: Rotation) =
        blockState.setValue(
            HorizontalDirectionalBlock.FACING, rotation.rotate(blockState.getValue(
                HorizontalDirectionalBlock.FACING)))

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) {
            Containers.dropContentsOnDestroy(state, newState, world, pos)
            super.onRemove(state, world, pos, newState, moved)
        }
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean {
        return true
    }

    override fun getAnalogOutputSignal(state: BlockState, world: Level, pos: BlockPos): Int {
        return AbstractContainerMenu.getRedstoneSignalFromBlockEntity(world.getBlockEntity(pos))
    }

    override fun useWithoutItem(
        state: BlockState,
        world: Level,
        pos: BlockPos,
        player: Player,
        hit: BlockHitResult
    ): InteractionResult {
        val basePos = getBasePosition(state, pos)
        // Remove any duplicate block entities that may exist
        world.getBlockEntity(basePos.above())?.setRemoved()
        val baseEntity = world.getBlockEntity(basePos)
        if (baseEntity !is CopperFridgeBlockEntity) return InteractionResult.SUCCESS

        player.openMenu(baseEntity)
        if (!player.level().isClientSide) {
            PiglinAi.angerNearbyPiglins(player, true)
        }
        return InteractionResult.SUCCESS
    }

    override fun getRenderShape(state: BlockState) = RenderShape.ENTITYBLOCK_ANIMATED

    override fun getFluidState(state: BlockState): FluidState? {
        return if (state.getValue(WATERLOGGED)) {
            Fluids.WATER.getSource(false)
        } else super.getFluidState(state)
    }

    enum class Type(val poserId: ResourceLocation) : StringRepresentable {
        RED(cobblemonResource("copper_fridge"));

        override fun getSerializedName(): String = name.lowercase()

        companion object {
            val CODEC = StringRepresentable.fromValues(::values)
        }
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

        val isFridge = neighborState.`is`(this)
        val part = state.getValue(PART)
        if (!isFridge && part == CopperFridgePart.TOP && neighborPos == pos.below()) {
            return Blocks.AIR.defaultBlockState()
        } else if (!isFridge && part == CopperFridgePart.BOTTOM && neighborPos == pos.above()) {
            return Blocks.AIR.defaultBlockState()
        }

        return state
    }

    override fun tick(state: BlockState, world: ServerLevel, pos: BlockPos, random: RandomSource) {
        val blockEntity = world.getBlockEntity(pos) as? CopperFridgeBlockEntity ?: return
        blockEntity.tick()
    }
}