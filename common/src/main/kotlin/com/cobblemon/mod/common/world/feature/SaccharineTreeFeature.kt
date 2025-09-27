/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.world.feature

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.tags.CobblemonBiomeTags
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.Direction.UP
import net.minecraft.tags.BlockTags
import net.minecraft.util.RandomSource
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block.UPDATE_ALL
import net.minecraft.world.level.block.Block.UPDATE_CLIENTS
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.LeavesBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.feature.Feature
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext
import net.minecraft.world.level.levelgen.feature.TreeFeature
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration

class SaccharineTreeFeature : Feature<BlockStateConfiguration>(BlockStateConfiguration.CODEC) {

    override fun place(context: FeaturePlaceContext<BlockStateConfiguration>): Boolean {
        val worldGenLevel: WorldGenLevel = context.level()
        val random = context.random()
        val origin = context.origin()

        val isGenerating = worldGenLevel.getChunk(origin).persistedStatus != ChunkStatus.FULL

        if (isGenerating) {
            val biome = worldGenLevel.getBiome(origin)
            if (!biome.`is`(CobblemonBiomeTags.ALLOWED_BIOMES_SACCHARINE_TREE)) {
                return false
            }
            val multiplier = when {
                biome.`is`(CobblemonBiomeTags.HAS_APRICORNS_SPARSE) -> 0.1F
                biome.`is`(CobblemonBiomeTags.HAS_APRICORNS_DENSE) -> 10F
                biome.`is`(CobblemonBiomeTags.HAS_APRICORNS_NORMAL) -> 1.0F
                else -> return false
            }

            if (random.nextFloat() > multiplier * Cobblemon.config.baseApricornTreeGenerationChance) {
                return false
            }
        }

        if (!worldGenLevel.getBlockState(origin.below()).`is`(BlockTags.DIRT)) {
            return false
        }

        // Determine tree height and variant
        val trunkHeight = if (random.nextBoolean()) 1 else 2
        val actualHeight = trunkHeight + 5

        for (y in 0 until actualHeight) {
            val pos = origin.relative(UP, y)
            if (!TreeFeature.isAirOrLeaves(worldGenLevel, pos)) {
                return false
            }
        }

        // Place the tree
        val potentialBeeNestPositions = mutableListOf<BlockPos>()
        val flowerPositions = isFlowerNearby(worldGenLevel, origin)

        // Create trunk
        val logState = CobblemonBlocks.SACCHARINE_LOG.defaultBlockState()
        for (y in 0 until trunkHeight) {
            val logPos = origin.relative(UP, y)
            worldGenLevel.setBlock(logPos, logState, UPDATE_CLIENTS)
        }
        
        var currentHeight = trunkHeight

        // Place patterns
        // Top Trunk Pattern
        placeTopTrunkPattern(worldGenLevel, origin.relative(UP, currentHeight), logState, potentialBeeNestPositions)
        currentHeight++

        // Leaf Start Pattern
        placeLeafStartPattern(
            worldGenLevel,
            origin.relative(UP, currentHeight),
            logState,
            CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
            potentialBeeNestPositions,
            random
        )
        currentHeight++

        // Big Leaf Pattern
        placeBigLeafPattern(worldGenLevel, origin.relative(UP, currentHeight), logState, CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState())
        currentHeight++

        // Other patterns (small, big, topper)
        placeSmallLeafPattern(
            worldGenLevel,
            origin.relative(UP, currentHeight),
            logState,
            CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
            Blocks.AIR.defaultBlockState(),
            random
        )
        currentHeight++

        // Big Leaf Pattern
        placeBigLeafPattern(worldGenLevel, origin.relative(UP, currentHeight), logState, CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState())
        currentHeight++

        // Random extension or Leaf Topper Pattern
        if (random.nextBoolean()) {
            placeLeafTopperPattern(
                worldGenLevel,
                origin.relative(UP, currentHeight),
                CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
                CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
                random
            )
        } else {
            // Small Leaf Pattern
            placeSmallLeafPattern(
                worldGenLevel,
                origin.relative(UP, currentHeight),
                logState,
                CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
                Blocks.AIR.defaultBlockState(),
                random
            )
            currentHeight++

            // Big Leaf Pattern
            placeBigLeafPattern(worldGenLevel, origin.relative(UP, currentHeight), logState, CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState())
            currentHeight++

            // Leaf Topper Pattern
            placeLeafTopperPattern(
                worldGenLevel,
                origin.relative(UP, currentHeight),
                CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
                CobblemonBlocks.SACCHARINE_LEAVES.defaultBlockState(),
                random
            )
        }

        // Place bee nest logic
        if (isGenerating || flowerPositions.isNotEmpty()) {
            placeBeeNest(worldGenLevel, potentialBeeNestPositions, flowerPositions)
        }

        return true
    }

    /**
     * A function to filter possible bee nest positions [locationsToCheck] to remove locations with a none empty block to the south.
     * @return A sub-set of bee nest positions with an empty block to the south (z + 1).
     */
    private fun filterBeeNestPositions(worldGenLevel: WorldGenLevel, locationsToCheck: List<BlockPos>): List<BlockPos> {
        // A sub-set of viable bee nest locations
        val viableBeeNestPositions: MutableList<BlockPos> = mutableListOf()
        for (pos in locationsToCheck) {
            // Block that is south of possible bee nest
            val southBlock: BlockPos = pos.offset(0, 0, 1)

            // Check for if the southern block is empty.
            if (worldGenLevel.isEmptyBlock(southBlock)) {
                viableBeeNestPositions.add(pos)
            }
        }

        return viableBeeNestPositions
    }

    private fun isFlowerNearby(worldGenLevel: WorldGenLevel, origin: BlockPos): List<BlockPos> {
        val flowerPositions = mutableListOf<BlockPos>()
        for (dx in -2..2) {
            for (dy in -2..2) {
                for (dz in -2..2) {
                    val pos = origin.offset(dx, dy, dz)
                    if (worldGenLevel.getBlockState(pos).`is`(BlockTags.FLOWERS)) {
                        flowerPositions.add(pos)
                    }
                }
            }
        }
        return flowerPositions
    }

    private fun placeBeeNest(worldGenLevel: WorldGenLevel, potentialBeeNestPositions: MutableList<BlockPos>, flowerPositions: List<BlockPos>) {
        val viableBeeNestPositions: List<BlockPos> = filterBeeNestPositions(worldGenLevel, potentialBeeNestPositions)

        val nestPos = viableBeeNestPositions.firstOrNull { pos ->
            flowerPositions.any { flowerPos -> flowerPos.closerThan(pos, 1.5) }
        } ?: viableBeeNestPositions.randomOrNull()

        val validNestPos = flowerPositions.firstOrNull { flowerPos ->
            nestPos?.closerThan(flowerPos, 1.5) == true
        }?.relative(Direction.SOUTH) ?: nestPos

        if (validNestPos != null && worldGenLevel.isEmptyBlock(validNestPos)) {
            val southFacingBeeNest = Blocks.BEE_NEST.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            setBlockIfClear(worldGenLevel, validNestPos, southFacingBeeNest)
        } else if (nestPos != null && worldGenLevel.isEmptyBlock(nestPos)) {
            // Natural Generation
            val southFacingBeeNest = Blocks.BEE_NEST.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH)
            setBlockIfClear(worldGenLevel, nestPos, southFacingBeeNest)
        }
    }

    private fun placeBigLeafPattern(worldGenLevel: WorldGenLevel, origin: BlockPos, logBlock: BlockState, leafBlock: BlockState) {
        val positions = listOf(
            origin.offset(-2, 0, 0),
            origin.offset(2, 0, 0),
            origin.offset(0, 0, -2),
            origin.offset(0, 0, 2),
            origin.offset(-1, 0, -2),
            origin.offset(1, 0, -2),
            origin.offset(-1, 0, 2),
            origin.offset(1, 0, 2),
            origin.offset(-2, 0, -1),
            origin.offset(-2, 0, 1),
            origin.offset(2, 0, -1),
            origin.offset(2, 0, 1),
            origin.offset(-1, 0, -1),
            origin.offset(1, 0, 1),
            origin.offset(-1, 0, 1),
            origin.offset(1, 0, -1),
            origin.offset(0, 0, 0), // Add center position
            origin.offset(1, 0, 0),
            origin.offset(-1, 0, 0),
            origin.offset(0, 0, 1),
            origin.offset(0, 0, -1),
            origin.offset(0, 0, 0)
        )

        for (pos in positions) {
            setBlockIfClear(worldGenLevel, pos, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
        }

        // Center trunk
        worldGenLevel.setBlock(origin, logBlock, UPDATE_CLIENTS)
    }

    private fun placeSmallLeafPattern(worldGenLevel: WorldGenLevel, origin: BlockPos, logBlock: BlockState, leafBlock: BlockState, specialBlock: BlockState, random: RandomSource) {
        val positions = listOf(
            origin.offset(-1, 0, 0),
            origin.offset(1, 0, 0),
            origin.offset(0, 0, -1),
            origin.offset(0, 0, 1)
        )

        for (pos in positions) {
            setBlockIfClear(worldGenLevel, pos, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
        }

        val specialPositions = listOf(
            origin.offset(-1, 0, -1),
            origin.offset(1, 0, 1),
            origin.offset(-1, 0, 1),
            origin.offset(1, 0, -1)
        )

        for (pos in specialPositions) {
            if (random.nextFloat() < 0.25f) {
                setBlockIfClear(worldGenLevel, pos, specialBlock)
            } else {
                setBlockIfClear(worldGenLevel, pos, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
            }
        }

        // Center trunk
        worldGenLevel.setBlock(origin, logBlock, UPDATE_CLIENTS)
    }

    private fun placeLeafTopperPattern(worldGenLevel: WorldGenLevel, origin: BlockPos, leafBlock: BlockState, specialBlock: BlockState, random: RandomSource) {
        val positions = listOf(
            origin.offset(-1, 0, 0),
            origin.offset(1, 0, 0),
            origin.offset(0, 0, -1),
            origin.offset(0, 0, 1),
            origin.offset(-1, 0, -1),
            origin.offset(1, 0, 1),
            origin.offset(-1, 0, 1),
            origin.offset(1, 0, -1)
        )

        for (pos in positions) {
            if (random.nextFloat() < 0.25f) {
                setBlockIfClear(worldGenLevel, pos, specialBlock)
            } else {
                setBlockIfClear(worldGenLevel, pos, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
            }
        }

        // Center leaf
        setBlockIfClear(worldGenLevel, origin, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
    }

    private fun placeLeafStartPattern(worldGenLevel: WorldGenLevel, origin: BlockPos, logBlock: BlockState, leafBlock: BlockState, potentialBeeNestPositions: MutableList<BlockPos>, random: RandomSource) {
        val positions = listOf(
            origin.offset(-1, 0, 0),
            origin.offset(1, 0, 0),
            origin.offset(0, 0, -1),
            origin.offset(0, 0, 1),
            origin.offset(-1, 0, -1),
            origin.offset(1, 0, 1),
            origin.offset(-1, 0, 1),
            origin.offset(1, 0, -1)
        )

        for (pos in positions) {
            if (random.nextFloat() < 0.25f) {
                potentialBeeNestPositions.add(pos)
            } else {
                setBlockIfClear(worldGenLevel, pos, leafBlock.setValue(LeavesBlock.DISTANCE, UPDATE_CLIENTS))
            }
        }

        // Center trunk
        worldGenLevel.setBlock(origin, logBlock, UPDATE_CLIENTS)
    }

    private fun placeTopTrunkPattern(worldGenLevel: WorldGenLevel, origin: BlockPos, logBlock: BlockState, potentialBeeNestPositions: MutableList<BlockPos>) {
        val positions = listOf(
            origin.offset(-1, 0, 0),
            origin.offset(1, 0, 0),
            origin.offset(0, 0, -1),
            origin.offset(0, 0, 1)
        )

        potentialBeeNestPositions.addAll(positions)

        // Center trunk
        worldGenLevel.setBlock(origin, logBlock, UPDATE_CLIENTS)
    }

    private fun setBlockIfClear(worldGenLevel: WorldGenLevel, blockPos: BlockPos, blockState: BlockState) {
        if (!TreeFeature.isAirOrLeaves(worldGenLevel, blockPos)) {
            return
        }
        worldGenLevel.setBlock(blockPos, blockState, UPDATE_ALL)
    }

    /*private fun isAir(testableWorld: TestableWorld, blockPos: BlockPos?): Boolean {
        return testableWorld.testBlockState(
            blockPos
        ) { blockState: BlockState ->
            blockState.`is`(
                Blocks.AIR
            )
        }
    }*/
}
