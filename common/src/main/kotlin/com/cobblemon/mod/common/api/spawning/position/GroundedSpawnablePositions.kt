/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.position

import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.SpawningZone
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.blockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.world.entity.Entity
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.FluidTags
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

/**
 * A type of area based spawnable position with a floor.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
abstract class FlooredSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : AreaSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone) {
    /** The block that the spawning is occurring on. */
    val baseBlock = zone.getBlockState(position.x, position.y, position.z)
    val baseBlockHolder: Holder<Block> by lazy { world.blockRegistry.wrapAsHolder(baseBlock.block) }
}

/**
 * A land spawnable position.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
open class GroundedSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : FlooredSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone)

/**
 * A spawnable position for entities that hang in the air under a ceiling block.
 */
open class HangingSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : AreaSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone) {
    override fun afterSpawn(action: SpawnAction<*>, entity: Entity) {
        super.afterSpawn(action, entity)
        if (entity is PokemonEntity && entity.pokemon.isWild()) {
            entity.brain.setMemory(CobblemonMemories.HANGING_FROM_CEILING, true)
            entity.brain.setMemory(CobblemonMemories.HANGING_CEILING_ANCHOR, entity.blockPosition())
            entity.setDeltaMovement(0.0, 0.0, 0.0)
            entity.isNoGravity = true
        }
    }
}

/**
 * A spawnable position that occurs at the bottom of a body of water.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
open class SeafloorSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : FlooredSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone) {
    override fun isSafeSpace(world: ServerLevel, pos: BlockPos, state: BlockState) = state.fluidState.`is`(FluidTags.WATER)
}

/**
 * A spawnable position that occurs at the bottom of bodies of lava.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
open class LavafloorSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : FlooredSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone) {
    override fun isSafeSpace(world: ServerLevel, pos: BlockPos, state: BlockState) = state.fluidState.`is`(FluidTags.LAVA)
}

open class SurfaceSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    val depth: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone
) : FlooredSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone)
