/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.block

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.tags.CobblemonBlockTags
import com.cobblemon.mod.common.block.TypeGemClusterBlock.Companion.SHOULD_GROW
import com.cobblemon.mod.common.block.TypeGemClusterBlock.Companion.STUNTED
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DirectionalBlock
import net.minecraft.world.level.block.state.BlockState
import java.util.*

class TypeGemCoreBlock(properties: Properties) : Block(properties) {

    companion object {
        const val MAX_CONNECTED_GEMS = 7
        const val MIN_DISTANCE_BETWEEN_GEMS = 1
        const val CONTINUATION_CHANCE = 0.8f

        val BLOCK_TO_CLUSTER: Map<ResourceLocation, Block> by lazy {
            mapOf(
                ResourceLocation.parse("cobblemon:type_gem_block_normal") to CobblemonBlocks.TYPE_GEM_CLUSTER_NORMAL,
                ResourceLocation.parse("cobblemon:type_gem_block_fire") to CobblemonBlocks.TYPE_GEM_CLUSTER_FIRE,
                ResourceLocation.parse("cobblemon:type_gem_block_water") to CobblemonBlocks.TYPE_GEM_CLUSTER_WATER,
                ResourceLocation.parse("cobblemon:type_gem_block_electric") to CobblemonBlocks.TYPE_GEM_CLUSTER_ELECTRIC,
                ResourceLocation.parse("cobblemon:type_gem_block_grass") to CobblemonBlocks.TYPE_GEM_CLUSTER_GRASS,
                ResourceLocation.parse("cobblemon:type_gem_block_ice") to CobblemonBlocks.TYPE_GEM_CLUSTER_ICE,
                ResourceLocation.parse("cobblemon:type_gem_block_fighting") to CobblemonBlocks.TYPE_GEM_CLUSTER_FIGHTING,
                ResourceLocation.parse("cobblemon:type_gem_block_poison") to CobblemonBlocks.TYPE_GEM_CLUSTER_POISON,
                ResourceLocation.parse("cobblemon:type_gem_block_ground") to CobblemonBlocks.TYPE_GEM_CLUSTER_GROUND,
                ResourceLocation.parse("cobblemon:type_gem_block_flying") to CobblemonBlocks.TYPE_GEM_CLUSTER_FLYING,
                ResourceLocation.parse("cobblemon:type_gem_block_psychic") to CobblemonBlocks.TYPE_GEM_CLUSTER_PSYCHIC,
                ResourceLocation.parse("cobblemon:type_gem_block_bug") to CobblemonBlocks.TYPE_GEM_CLUSTER_BUG,
                ResourceLocation.parse("cobblemon:type_gem_block_rock") to CobblemonBlocks.TYPE_GEM_CLUSTER_ROCK,
                ResourceLocation.parse("cobblemon:type_gem_block_ghost") to CobblemonBlocks.TYPE_GEM_CLUSTER_GHOST,
                ResourceLocation.parse("cobblemon:type_gem_block_dragon") to CobblemonBlocks.TYPE_GEM_CLUSTER_DRAGON,
                ResourceLocation.parse("cobblemon:type_gem_block_dark") to CobblemonBlocks.TYPE_GEM_CLUSTER_DARK,
                ResourceLocation.parse("cobblemon:type_gem_block_steel") to CobblemonBlocks.TYPE_GEM_CLUSTER_STEEL,
                ResourceLocation.parse("cobblemon:type_gem_block_fairy") to CobblemonBlocks.TYPE_GEM_CLUSTER_FAIRY
            )
        }

        fun setStuntState(level: LevelAccessor, gemBlocks: List<Pair<BlockState, BlockPos>>, stunted: Boolean) {
            for ((_, gemPos) in gemBlocks) {
                for (dir in Direction.entries) {
                    val neighborPos = gemPos.relative(dir)
                    val neighborState = level.getBlockState(neighborPos)

                    if (neighborState.block is TypeGemClusterBlock) {
                        if (neighborState.hasProperty(STUNTED) && neighborState.getValue(STUNTED) != stunted) {

                            var updatedState = neighborState.setValue(STUNTED, stunted)

                            if (updatedState.hasProperty(SHOULD_GROW)) {
                                updatedState = updatedState.setValue(SHOULD_GROW, !stunted)
                            }

                            level.setBlock(neighborPos, updatedState, 3)
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

                for (direction in Direction.entries) {
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

    override fun randomTick(state: BlockState, level: ServerLevel, pos: BlockPos, random: RandomSource) {
        grow(level, pos, random)
    }

    fun forceGrow(level: WorldGenLevel, pos: BlockPos, random: RandomSource, percentage: Float) {
        val desiredLength = MAX_CONNECTED_GEMS * percentage

        while (true) {
            val growInfo = grow(level, pos, random, true)
            val grown = growInfo.first
            val clusterSize = growInfo.second

            if (!grown || clusterSize >= desiredLength) break
        }
    }

    private fun grow(
        level: WorldGenLevel,
        pos: BlockPos,
        random: RandomSource,
        forced: Boolean = false
    ): Pair<Boolean, Int> {
        val connectedGems = getConnectedGemBlocks(level, pos) // maybe we can store this and access it for better performance? check for changes in list rather than checking each time grow is called? idk
        val gemCount = connectedGems.size
        val overLimit = gemCount >= MAX_CONNECTED_GEMS
        
        if (overLimit) {
            // if there is no open air pockets to grow any new clusters then exit early
            if (!hasBreathingRoom(level, connectedGems)) {
                return Pair(false, gemCount)
            }
            // max type gem block count met, tell clusters to grow but not turn into blocks
            setStuntState(level, connectedGems, true)
            //return Pair(false, gemCount)
        } else {
            // make sure connected clusters know they can grow and turn into blocks
            setStuntState(level, connectedGems, false)
        }

        val javaRandom = java.util.Random(random.nextLong())

        for ((gemState, gemPos) in connectedGems.shuffled(javaRandom)) {
            val registryKey = BuiltInRegistries.BLOCK.getKey(gemState.block)
            val clusterBlock = BLOCK_TO_CLUSTER[registryKey]

            for (dir in Direction.entries.shuffled(javaRandom)) {
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

                    level.setBlock(targetPos, placeState, UPDATE_ALL)
                }

                if (forced && clusterBlock != null) {
                    forceAdvanceClusterGrowth(level, targetPos, random)
                }

                val updatedGemCount = if (forced) getConnectedGemBlocks(level, pos).size else connectedGems.size
                return Pair(true, updatedGemCount)
            }
        }

        // println("[TypeGemCoreBlock] No valid spot found for gem placement.")
        return Pair(false, connectedGems.size)
    }

    private fun forceAdvanceClusterGrowth(level: WorldGenLevel, clusterPos: BlockPos, random: RandomSource) {
        repeat(5) {
            val clusterState = level.getBlockState(clusterPos)
            val clusterBlock = clusterState.block as? TypeGemClusterBlock ?: return
            clusterBlock.advanceGrowth(clusterState, level, clusterPos, random)
        }
    }

    private fun hasBreathingRoom(level: WorldGenLevel, gems: List<Pair<BlockState, BlockPos>>): Boolean {
        for ((_, gemPos) in gems) {
            for (dir in Direction.entries) {
                if (level.getBlockState(gemPos.relative(dir)).isAir) {
                    return true
                }
            }
        }
        return false
    }

    private fun isPositionValidForGrowth(level: WorldGenLevel, pos: BlockPos): Boolean {
        return Direction.entries.none { dir ->
            val nearby = pos.relative(dir)
            isGem(level.getBlockState(nearby)) &&
                    pos.distManhattan(nearby) < MIN_DISTANCE_BETWEEN_GEMS
        }
    }

    private fun isGem(state: BlockState): Boolean {
        return state.`is`(CobblemonBlockTags.TYPE_GEM_BLOCKS)
    }

    override fun isRandomlyTicking(state: BlockState): Boolean = true
}
