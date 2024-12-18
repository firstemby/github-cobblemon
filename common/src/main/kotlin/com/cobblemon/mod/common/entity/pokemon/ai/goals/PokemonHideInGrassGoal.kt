/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.goals

import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.toVec3d
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class PokemonHideInGrassGoal(private val pokemonEntity: PokemonEntity): Goal() {

    companion object {
        private const val GRASS_SEARCH_HORIZONTAL_RANGE = 16.0
        private const val GRASS_SEARCH_VERTICAL_RANGE = 2.0
        private val SECONDS_IN_SAME_GRASS = 5 .. 10
        private val VALID_GRASS_BLOCKS = setOf(
            Blocks.SHORT_GRASS,
            Blocks.TALL_GRASS,
            Blocks.FERN,
            Blocks.LARGE_FERN,
        )
    }

    private var pickedGrassBlockPos: BlockPos? = null
    private var ticksInGrass = 0
    private var wantedTicksInGrass = 0

    override fun canUse(): Boolean =
        pokemonEntity.pokemon.species.behaviour.moving.hideInGrass
        && pokemonEntity.pokemon.isWild()
        && getNearbyGrassBlocks().isNotEmpty()

    override fun start() {
        super.start()

        pickedGrassBlockPos = getNearbyGrassBlocks().random()
        ticksInGrass = 0
        wantedTicksInGrass = pokemonEntity.random.nextIntBetweenInclusive(SECONDS_IN_SAME_GRASS.first, SECONDS_IN_SAME_GRASS.last) * 20
    }

    override fun tick() {
        super.tick()

        val grassBlockCenterPos = pickedGrassBlockPos?.bottomCenter ?: return stop()
        val couldMoveToGrass = pokemonEntity.navigation.moveTo(
            grassBlockCenterPos.x,
            grassBlockCenterPos.y,
            grassBlockCenterPos.z,
            1.0
        )

        if (!couldMoveToGrass) return stop()

        ticksInGrass++
    }

    override fun canContinueToUse(): Boolean {
        if (ticksInGrass >= wantedTicksInGrass) return false

        pickedGrassBlockPos?.let {
            val grassStillExists =  isGrass(pokemonEntity.level().getBlockState(it))
            if (!grassStillExists) return false

            val grassBox = AABB.ofSize(it.toVec3d(), 0.5, 0.5, 0.5)
            val isGrassOccupiedByAnotherEntity = pokemonEntity.level().getEntities(pokemonEntity, grassBox).isNotEmpty()
            if (isGrassOccupiedByAnotherEntity) {
                return false
            }
        } ?: return false

        return true
    }

    override fun stop() {
        super.stop()

        pickedGrassBlockPos = null
        ticksInGrass = 0
        pokemonEntity.navigation.stop()
    }

    private fun getNearbyGrassBlocks(): List<BlockPos> {
        val searchBox = AABB.ofSize(
            pokemonEntity.position(),
            GRASS_SEARCH_HORIZONTAL_RANGE,
            GRASS_SEARCH_VERTICAL_RANGE,
            GRASS_SEARCH_HORIZONTAL_RANGE
        )

        val grassBlocks: MutableList<BlockPos> = mutableListOf()
        BlockPos.betweenClosedStream(searchBox).forEach {
            val blockState = pokemonEntity.level().getBlockState(it)
            if (isGrass(blockState)) {
                grassBlocks.add(BlockPos(it))
            }
        }

        return grassBlocks
    }

    private fun isGrass(blockState: BlockState): Boolean = VALID_GRASS_BLOCKS.contains(blockState.block)
}