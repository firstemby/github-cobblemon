/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.util.rotateShape
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.AttachFace
import net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class MagnetBlock(properties: Properties) : SixFaceRotationalBlock(properties) {
    companion object {
        val CODEC: MapCodec<MagnetBlock> = RecordCodecBuilder.mapCodec {
            it.group(propertiesCodec()).apply(it, ::MagnetBlock)
        }

        val FLOOR_NORTH = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.1875, 0.75)
        val WALL_NORTH = Shapes.box(0.25, 0.25, 0.8125, 0.75, 0.75, 1.0)
        val CEILING_NORTH = Shapes.box(0.25, 0.8125, 0.25, 0.75, 1.0, 0.75)
    }

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH)
            .setValue(FACE, AttachFace.WALL)
            .setValue(WATERLOGGED, false)
        )
    }

    override fun codec(): MapCodec<MagnetBlock> = CODEC

    override fun getShape(state: BlockState, blockGetter: BlockGetter, pos: BlockPos, collisionContext: CollisionContext): VoxelShape {
        val direction = state.getValue(FACING)
        return when (state.getValue(FACE)) {
            AttachFace.FLOOR ->
                if (direction.axis === Direction.Axis.X) {
                    rotateShape(Direction.NORTH, Direction.WEST, FLOOR_NORTH)
                } else FLOOR_NORTH
            AttachFace.WALL ->
                when (direction) {
                    Direction.NORTH, Direction.UP, Direction.DOWN -> WALL_NORTH
                    else -> rotateShape(Direction.NORTH, direction, WALL_NORTH)
                }
            AttachFace.CEILING ->
                if (direction.axis === Direction.Axis.X) {
                    rotateShape(Direction.NORTH, Direction.WEST, CEILING_NORTH)
                } else CEILING_NORTH
        }
    }

    override fun canSurvive(state: BlockState, level: LevelReader, pos: BlockPos): Boolean {
        return true
    }
}