/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.block.TypeGemClusterBlock.Companion.SHOULD_GROW
import com.cobblemon.mod.common.block.TypeGemClusterBlock.Companion.STUNTED
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import java.util.*

class TypeGemCoreBlock(properties: Properties) : Block(properties) {

    companion object {
        const val MAX_CONNECTED_GEMS = 7
        const val MIN_DISTANCE_BETWEEN_GEMS = 1
        const val CONTINUATION_CHANCE = 0.8f
        const val INACTIVE_WAKE_RECHECK_CHANCE = 10

        val ACTIVE: BooleanProperty = BooleanProperty.create("active")
        private val DIRECTIONS = Direction.entries.toTypedArray()

        fun setStuntState(level: LevelAccessor, gemBlocks: List<Pair<BlockState, BlockPos>>, stunted: Boolean) {
            for ((_, gemPos) in gemBlocks) {
                for (dir in DIRECTIONS) {
                    val neighborPos = gemPos.relative(dir)
                    val neighborState = level.getBlockState(neighborPos)

                    if (neighborState.block is TypeGemClusterBlock) {
                        if (neighborState.hasProperty(STUNTED) && neighborState.getValue(STUNTED) != stunted) {

                            var updatedState = neighborState.setValue(STUNTED, stunted)

                            if (updatedState.hasProperty(SHOULD_GROW)) {
                                updatedState = updatedState.setValue(SHOULD_GROW, !stunted)
                            }

                            level.setBlock(neighborPos, updatedState, UPDATE_CLIENTS)
                        }
                    }
                }
            }
        }

        fun getConnectedGemBlocks(level: BlockGetter, pos: BlockPos): List<Pair<BlockState, BlockPos>> {
            val connectedGems = mutableListOf<Pair<BlockState, BlockPos>>()
            val visited = mutableSetOf<BlockPos>()
            val queue: Queue<BlockPos> = LinkedList()

            visited.add(pos)
            queue.add(pos)
            connectedGems.add(level.getBlockState(pos) to pos)

            while (queue.isNotEmpty()) {
                val current = queue.poll()

                for (direction in DIRECTIONS) {
                    val neighbor = current.relative(direction)
                    if (visited.add(neighbor)) {
                        val state = level.getBlockState(neighbor)
                        if (state.`is`(CobblemonBlockTags.TYPE_GEM_BLOCKS)) {
                            connectedGems.add(state to neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
            }
            return connectedGems
        }
    }

    init {
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, true))
    }

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        if (!state.getValue(ACTIVE)) {
            if (random.nextInt(INACTIVE_WAKE_RECHECK_CHANCE) != 0) {
                return
            }

            if (shouldWakeFromSleep(level, pos)) {
                level.setBlock(pos, state.setValue(ACTIVE, true), UPDATE_CLIENTS)
            }
            return
        }

        val result = grow(level, pos, random)
        if (!result.grew && !result.shouldKeepTicking) {
            level.setBlock(pos, state.setValue(ACTIVE, false), UPDATE_CLIENTS)
        }
    }

    fun forceGrow(level: WorldGenLevel, pos: BlockPos, random: RandomSource, percentage: Float) {
        val desiredLength = MAX_CONNECTED_GEMS * percentage

        while (true) {
            val growInfo = grow(level, pos, random, true)
            val grown = growInfo.grew
            val clusterSize = growInfo.clusterSize

            if (!grown || clusterSize >= desiredLength) break
        }
    }

    private data class GrowResult(val grew: Boolean, val clusterSize: Int, val shouldKeepTicking: Boolean)

    private fun grow(
        level: WorldGenLevel,
        pos: BlockPos,
        random: RandomSource,
        forced: Boolean = false
    ): GrowResult {
        val connectedGems = getConnectedGemBlocks(level, pos) // maybe we can store this and access it for better performance? check for changes in list rather than checking each time grow is called? idk
        val gemCount = connectedGems.size
        val overLimit = gemCount >= MAX_CONNECTED_GEMS
        
        if (overLimit) {
            // if there is no open air pockets to grow any new clusters then exit early
            if (!hasBreathingRoom(level, connectedGems)) {
                return GrowResult(false, gemCount, false)
            }
            // max type gem block count met, tell clusters to grow but not turn into blocks
            setStuntState(level, connectedGems, true)
            return GrowResult(false, gemCount, false)
        } else {
            // make sure connected clusters know they can grow and turn into blocks
            setStuntState(level, connectedGems, false)
        }

        val gemStart = random.nextInt(gemCount)
        for (gemOffset in 0 until gemCount) {
            val (gemState, gemPos) = connectedGems[(gemStart + gemOffset) % gemCount]
            val clusterBlock = TypeGemClusterBlock.clusterFromGemBlock(gemState.block)
            val directionStart = random.nextInt(DIRECTIONS.size)

            for (dirOffset in DIRECTIONS.indices) {
                val dir = DIRECTIONS[(directionStart + dirOffset) % DIRECTIONS.size]
                val targetPos = gemPos.relative(dir)
                val targetState = level.getBlockState(targetPos)

                if (!targetState.isAir) continue
                if (!isPositionValidForGrowth(level, targetPos)) continue

                // Place gem cluster
                if (clusterBlock != null) {
                    var placeState = clusterBlock.defaultBlockState()
                        .setValue(DirectionalBlock.FACING, dir)
                        .setValue(TypeGemClusterBlock.STAGE, 0)
                        .setValue(SHOULD_GROW, true)

                    level.setBlock(targetPos, placeState, UPDATE_CLIENTS)
                }

                if (forced && clusterBlock != null) {
                    forceAdvanceClusterGrowth(level, targetPos, random)
                }

                val updatedGemCount = if (forced) getConnectedGemBlocks(level, pos).size else connectedGems.size
                return GrowResult(true, updatedGemCount, true)
            }
        }

        // println("[TypeGemCoreBlock] No valid spot found for gem placement.")
        return GrowResult(false, connectedGems.size, false)
    }

    private fun forceAdvanceClusterGrowth(level: WorldGenLevel, clusterPos: BlockPos, random: RandomSource) {
        repeat(5) {
            val clusterState = level.getBlockState(clusterPos)
            val clusterBlock = clusterState.block as? TypeGemClusterBlock ?: return
            clusterBlock.advanceGrowth(clusterState, level, clusterPos, random)
        }
    }

    private fun shouldWakeFromSleep(level: ServerLevel, pos: BlockPos): Boolean {
        val connectedGems = getConnectedGemBlocks(level, pos)
        if (connectedGems.size >= MAX_CONNECTED_GEMS) {
            return false
        }

        return hasBreathingRoom(level, connectedGems)
    }

    private fun hasBreathingRoom(level: WorldGenLevel, gems: List<Pair<BlockState, BlockPos>>): Boolean {
        for ((_, gemPos) in gems) {
            for (dir in DIRECTIONS) {
                if (level.getBlockState(gemPos.relative(dir)).isAir) {
                    return true
                }
            }
        }
        return false
    }

    private fun isPositionValidForGrowth(level: WorldGenLevel, pos: BlockPos): Boolean {
        return DIRECTIONS.none { dir ->
            val nearby = pos.relative(dir)
            isGem(level.getBlockState(nearby)) &&
                    pos.distManhattan(nearby) < MIN_DISTANCE_BETWEEN_GEMS
        }
    }

    private fun isGem(state: BlockState): Boolean {
        return state.`is`(CobblemonBlockTags.TYPE_GEM_BLOCKS)
    }

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, block: Block, fromPos: BlockPos, isMoving: Boolean) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving)
        if (!level.isClientSide && !state.getValue(ACTIVE)) {
            level.setBlock(pos, state.setValue(ACTIVE, true), UPDATE_CLIENTS)
        }
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        super.createBlockStateDefinition(builder)
        builder.add(ACTIVE)
    }

    override fun isRandomlyTicking(state: BlockState): Boolean = true
}
