package com.cobblemon.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.StringRepresentable
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.LevelEvent
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathComputationType

/**
 * Based off PCBlock's double block functionality, but for less specific use cases
 *
 * @author whatsy
 * @since September 30, 2026
 * @see PCBlock
 */
open class DoubleBlock(properties: Properties): Block(properties) {

    companion object {
        val HALF: Property<Half> = EnumProperty.create("half", Half::class.java)
    }

    enum class Half(private val label: String): StringRepresentable {
        TOP("top"),
        BOTTOM("bottom"), ;
        override fun getSerializedName(): String = label
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HALF, Half.BOTTOM)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
        )
    }

    fun getPositionOfOtherPart(state: BlockState, pos: BlockPos): BlockPos {
        return if(state.getValue(HALF) == Half.BOTTOM) {
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

    private fun isBase(state: BlockState): Boolean = state.getValue(HALF) == Half.BOTTOM

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        level.setBlock(pos.above(),state.setValue(HALF, Half.TOP), 3)
        level.blockUpdated(pos, Blocks.AIR)
        state.updateNeighbourShapes(level, pos, 3)
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> {
        return if (state.getValue(HALF) == Half.TOP) emptyList() else super.getDrops(state, params)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState? {
        val abovePosition = context.clickedPos.above()
        val world = context.level
        if (world.getBlockState(abovePosition).canBeReplaced(context) && !world.isOutsideBuildHeight(abovePosition)) {
            return defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, context.horizontalDirection)
                .setValue(HALF, Half.BOTTOM)
        }

        return null
    }

    override fun playerWillDestroy(world: Level, pos: BlockPos, state: BlockState, player: Player): BlockState {
        if (!world.isClientSide && player.isCreative) {
            var blockPos: BlockPos = BlockPos.ZERO
            var blockState: BlockState = state
            val part = state.getValue(HALF)
            if (part == Half.TOP && world.getBlockState(pos.below().also { blockPos = it }).also { blockState = it }.`is`(state.block) && blockState.getValue(HALF) == Half.BOTTOM) {
                val blockState2 = if (blockState.fluidState.`is`(Fluids.WATER)) Blocks.WATER.defaultBlockState() else Blocks.AIR.defaultBlockState()
                world.setBlock(blockPos, blockState2, UPDATE_ALL or UPDATE_SUPPRESS_DROPS)
                world.levelEvent(player, LevelEvent.PARTICLES_DESTROY_BLOCK, blockPos, getId(blockState))
            }
        }
        return super.playerWillDestroy(world, pos, state, player)
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(
        blockState: BlockState,
        pathComputationType: PathComputationType
    ): Boolean = false

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        builder.add(HorizontalDirectionalBlock.FACING)
        builder.add(HALF)
    }

    @Deprecated("Deprecated in Java")
    override fun rotate(blockState: BlockState, rotation: Rotation) =
        blockState.setValue(
            HorizontalDirectionalBlock.FACING, rotation.rotate(blockState.getValue(
                HorizontalDirectionalBlock.FACING)))

    @Deprecated("Deprecated in Java")
    override fun mirror(blockState: BlockState, mirror: Mirror): BlockState {
        return blockState.rotate(mirror.getRotation(blockState.getValue(HorizontalDirectionalBlock.FACING)))
    }

    override fun onRemove(state: BlockState, world: Level, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.`is`(newState.block)) super.onRemove(state, world, pos, newState, moved)
    }

    override fun updateShape(
        state: BlockState,
        direction: Direction,
        neighborState: BlockState,
        world: LevelAccessor,
        pos: BlockPos,
        neighborPos: BlockPos
    ): BlockState {
        val isThis = neighborState.`is`(this)
        val part = state.getValue(HALF)
        if (!isThis && part == Half.TOP && neighborPos == pos.below()) {
            return Blocks.AIR.defaultBlockState()
        } else if (!isThis && part == Half.BOTTOM && neighborPos == pos.above()) {
            return Blocks.AIR.defaultBlockState()
        }

        return state
    }

}