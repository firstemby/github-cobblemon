package com.cobblemon.mod.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.StringRepresentable
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.EnumProperty
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class ScarekrowBlock(properties: Properties): DoubleBlock(properties) {

    companion object {
       val POSE = EnumProperty.create("pose", Pose::class.java)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HALF, Half.BOTTOM)
            .setValue(POSE, Pose.DEFAULT)
            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block?, BlockState?>) {
        builder.add(POSE)
        super.createBlockStateDefinition(builder)
    }

    @Deprecated("Deprecated in Java")
    override fun isPathfindable(
        blockState: BlockState,
        pathComputationType: PathComputationType
    ): Boolean = false

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (player !is ServerPlayer) return InteractionResult.SUCCESS

        level.setBlock(pos, state.cycle(POSE), 3)
        return InteractionResult.SUCCESS
    }

    enum class Pose(private val label: String): StringRepresentable {
        DEFAULT("default"),
        RELAXED("relaxed"),
        SCARY("scary");
        override fun getSerializedName(): String = label
    }

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape {
        return super.getShape(state, level, pos, context)
    }

}