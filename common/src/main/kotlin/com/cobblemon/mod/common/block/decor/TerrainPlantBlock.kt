/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block.decor

import com.cobblemon.mod.common.CobblemonBlockEntities
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.block.entity.TerrainPlantBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.TallFlowerBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class TerrainPlantBlock(properties: Properties) : TallFlowerBlock(properties), EntityBlock {

    init {
        registerDefaultState(stateDefinition.any()
            .setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(GLOWING, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(HALF, GLOWING)
    }
    override fun isValidBonemealTarget(level: LevelReader, pos: BlockPos, state: BlockState): Boolean {
        return false
    }

    override fun tick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (state.`is`(CobblemonBlocks.LUCIBELL)) { this.checkInside(level, pos, state) }
    }

    private fun checkInside(level: Level, pos: BlockPos, state: BlockState) {
        // Only lower will handle this
        val upperState = level.getBlockState(pos.above()).takeIf { it.`is`(CobblemonBlocks.LUCIBELL) && it.getValue(HALF) == DoubleBlockHalf.UPPER }

        val entityInside = level.getEntities(null, state.getShape(level, pos).bounds().move(pos)).isNotEmpty()
                || upperState?.let { level.getEntities(null, it.getShape(level, pos.above()).bounds().move(pos.above())).isNotEmpty() } ?: false

        val glowingState = state.getValue(GLOWING)
        if (glowingState != entityInside) {
            level.setBlock(pos, state.setValue(GLOWING, entityInside), UPDATE_CLIENTS)
            upperState?.let { level.setBlock(pos.above(), it.setValue(GLOWING, entityInside), UPDATE_CLIENTS) }
        }
        if (entityInside) level.scheduleTick(pos, this, 30) // tick occasionally to check if player inside
    }

    override fun entityInside(state: BlockState, level: Level, pos: BlockPos, entity: Entity) {
        // Lucibell lower block will be our 'driver'
        if (!level.isClientSide && !state.getValue(GLOWING) && state.`is`(CobblemonBlocks.LUCIBELL)) {
            val halfState = state.getValue(HALF)
            if (halfState == DoubleBlockHalf.UPPER) {
                val lowerState = level.getBlockState(pos.below())
                    .takeIf { it.`is`(CobblemonBlocks.LUCIBELL) && it.getValue(HALF) == DoubleBlockHalf.LOWER }
                    ?: return
                (lowerState.block as TerrainPlantBlock).checkInside(level, pos.below(), lowerState)
            } else {
                checkInside(level, pos, state)
            }
        }
    }

    override fun getShape(state: BlockState, world: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = AABB

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity {
        return TerrainPlantBlockEntity(pos, state)
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T?>
    ): BlockEntityTicker<T?>? {
        return if (level.isClientSide) null else BaseEntityBlock.createTickerHelper(blockEntityType as BlockEntityType<*>,
            CobblemonBlockEntities.TERRAIN_PLANT, TerrainPlantBlockEntity::serverTick) as BlockEntityTicker<T?>?
    }

    companion object {
        private val AABB = Shapes.box(0.125, 0.0, 0.125, 0.875, 1.0, 0.875)
        val GLOWING = BooleanProperty.create("glowing")

    }
}
