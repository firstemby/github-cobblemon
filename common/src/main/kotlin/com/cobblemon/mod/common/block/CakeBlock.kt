/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.block.entity.CakeBlockEntity
import com.cobblemon.mod.common.item.components.FoodColourComponent

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.DyeColor
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DirectionProperty
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

abstract class CakeBlock(settings: Properties): BaseEntityBlock(settings) {
    companion object {
        val FACING: DirectionProperty = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL)

        val SHAPE_BY_BITE = arrayOf(
                box(1.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(3.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(5.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(7.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(9.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(11.0, 0.0, 1.0, 15.0, 9.0, 15.0),
                box(13.0, 0.0, 1.0, 15.0, 9.0, 15.0)
        )

        enum class Rotation {
            NONE,
            CLOCKWISE_90,
            CLOCKWISE_180,
            COUNTERCLOCKWISE_90
        }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(FACING)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)

        if (placer != null) {
            level.setBlock(pos, state.setValue(FACING, placer.direction.opposite), 2)
        }

        val blockEntity = level.getBlockEntity(pos) as? CakeBlockEntity
        blockEntity?.initializeFromItemStack(stack)
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState): ItemStack {
        val blockEntity = level.getBlockEntity(pos) as? CakeBlockEntity ?: return ItemStack.EMPTY
        return blockEntity.toItemStack()
    }

    override fun getShape(
            state: BlockState,
            level: BlockGetter,
            pos: BlockPos,
            context: CollisionContext
    ): VoxelShape {
        val bites = getBites(level, pos)
        val blockEntity = level.getBlockEntity(pos) as? CakeBlockEntity
        val maxBites = blockEntity?.maxBites ?: 6

        val visualStage = ((bites * SHAPE_BY_BITE.size) / (maxBites + 1))
                .coerceIn(0, SHAPE_BY_BITE.lastIndex)

        val baseShape = SHAPE_BY_BITE[visualStage]

        return when (state.getValue(FACING)) {
            Direction.NORTH -> baseShape
            Direction.SOUTH -> rotateShape(baseShape, Rotation.CLOCKWISE_180)
            Direction.WEST -> rotateShape(baseShape, Rotation.COUNTERCLOCKWISE_90)
            Direction.EAST -> rotateShape(baseShape, Rotation.CLOCKWISE_90)
            else -> baseShape
        }
    }

    private fun rotateShape(shape: VoxelShape, rotation: Rotation): VoxelShape {
        var newShape = net.minecraft.world.phys.shapes.Shapes.empty()

        for (box in shape.toAabbs()) {
            val minX = box.minX
            val minY = box.minY
            val minZ = box.minZ
            val maxX = box.maxX
            val maxY = box.maxY
            val maxZ = box.maxZ

            val (rMinX, rMinZ, rMaxX, rMaxZ) = when (rotation) {
                Rotation.NONE -> arrayOf(minX, minZ, maxX, maxZ)
                Rotation.CLOCKWISE_90 -> arrayOf(minZ, 1.0 - maxX, maxZ, 1.0 - minX)
                Rotation.CLOCKWISE_180 -> arrayOf(1.0 - maxX, 1.0 - maxZ, 1.0 - minX, 1.0 - minZ)
                Rotation.COUNTERCLOCKWISE_90 -> arrayOf(1.0 - maxZ, minX, 1.0 - minZ, maxX)
            }

            val rotatedVoxel = net.minecraft.world.phys.shapes.Shapes.box(rMinX, minY, rMinZ, rMaxX, maxY, rMaxZ)

            newShape = net.minecraft.world.phys.shapes.Shapes.or(newShape, rotatedVoxel)
        }

        return newShape
    }

    override fun spawnDestroyParticles(
            level: Level,
            player: Player,
            pos: BlockPos,
            state: BlockState
    ) {
        val blockEntity = level.getBlockEntity(pos) as? CakeBlockEntity
        val color = blockEntity?.foodColourComponent?.colours?.lastOrNull()
        val woolBlock = when (color) {
            DyeColor.RED -> Blocks.RED_WOOL
            DyeColor.ORANGE -> Blocks.ORANGE_WOOL
            DyeColor.YELLOW -> Blocks.YELLOW_WOOL
            DyeColor.LIME -> Blocks.LIME_WOOL
            DyeColor.GREEN -> Blocks.GREEN_WOOL
            DyeColor.CYAN -> Blocks.CYAN_WOOL
            DyeColor.LIGHT_BLUE -> Blocks.LIGHT_BLUE_WOOL
            DyeColor.BLUE -> Blocks.BLUE_WOOL
            DyeColor.PURPLE -> Blocks.PURPLE_WOOL
            DyeColor.MAGENTA -> Blocks.MAGENTA_WOOL
            DyeColor.PINK -> Blocks.PINK_WOOL
            else -> Blocks.WHITE_WOOL
        }

        level.levelEvent(player, 2001, pos, getId(woolBlock.defaultBlockState()))
    }

    fun getFoodColourComponent(level: BlockGetter, pos: BlockPos): FoodColourComponent? {
        return (level.getBlockEntity(pos) as? CakeBlockEntity)?.foodColourComponent
    }

    fun setFoodColourComponent(level: BlockGetter, pos: BlockPos, foodColourComponent: FoodColourComponent) {
        (level.getBlockEntity(pos) as? CakeBlockEntity)?.foodColourComponent = foodColourComponent
    }

    fun getBites(level: BlockGetter, pos: BlockPos): Int = (level.getBlockEntity(pos) as? CakeBlockEntity)?.bites ?: 0
    fun setBites(level: BlockGetter, pos: BlockPos, bites: Int) {
        (level.getBlockEntity(pos) as? CakeBlockEntity)?.bites = bites
    }
}