/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.block.entity.MistleafFlowerBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.level.material.MapColor
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class MistleafFlowerBlock(lightLevel: (BlockState) -> Int) :
    EntityBlock,
    TallFlowerBlock(
        Properties.of()
            .mapColor(MapColor.PLANT)
            .noCollission()
            .instabreak()
            .sound(SoundType.GRASS)
            .offsetType(OffsetType.XZ)
            .ignitedByLava()
            .pushReaction(PushReaction.DESTROY)
            .lightLevel(lightLevel)
    ) {

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(GLOWING, 0))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HALF, GLOWING)
    }
    override fun isValidBonemealTarget(level: LevelReader, pos: BlockPos, state: BlockState): Boolean {
        return false
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = AABB

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity? {
        return MistleafFlowerBlockEntity(pos, state)
    }

    override fun <T : BlockEntity?> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T?>? {
        return if (level.isClientSide) null else BaseEntityBlock.createTickerHelper(blockEntityType as BlockEntityType<*>,
            CobblemonBlockEntities.MISTLEAF_FLOWER, MistleafFlowerBlockEntity::serverTick) as BlockEntityTicker<T?>?
    }

    companion object {
        private val AABB = Shapes.box(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)
        val GLOWING = IntegerProperty.create("glowing", 0, 8)
    }

    override fun hasAnalogOutputSignal(state: BlockState): Boolean { return true }
    override fun getAnalogOutputSignal(state: BlockState, level: Level, pos: BlockPos): Int {
        return state.getValue(GLOWING) as Int
    }
}
