/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.position

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.SpawningZone
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.condition.ClingingTypeSpawningCondition
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.block.SilkThreadBlock
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.util.blockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
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
 * A spawnable position for Pokemon that hang beneath leaves from a column of [SilkThreadBlock]s.
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
        if (entity !is PokemonEntity || !entity.pokemon.isWild()) return
        if (placeThreadColumn(entity.blockPosition())) {
            entity.startHangingFromThread()
        }
    }

    // we need to place the super special thread block for our hanging boys for **immersion**
    private fun placeThreadColumn(monPos: BlockPos): Boolean {
        val anchorOffset = (1..SilkThreadBlock.MAX_LENGTH).firstOrNull { offset ->
            SilkThreadBlock.isValidAnchor(world.getBlockState(monPos.above(offset)))
        } ?: return false
        for (offset in (anchorOffset - 1) downTo 0) {
            val pos = monPos.above(offset)
            if (world.getBlockState(pos).isAir) {
                world.setBlockAndUpdate(pos, CobblemonBlocks.SILK_THREAD.defaultBlockState())
            }
        }
        return world.getBlockState(monPos).`is`(CobblemonBlocks.SILK_THREAD)
    }
}

/**
 * A spawnable position for Pokemon that cling to a face of an adjacent block without gravity.
 * The [attachments] map holds every neighbouring block, keyed by the direction from the
 * Pokemon towards that block. This can be used for us making it so cocoon mons spawn clung
 * to surfaces and maybe allow for some mons like monkeys or spiders / worms to spawn on the
 * sides of logs or caves
 */
open class ClingingSpawnablePosition(
    cause: SpawnCause,
    world: ServerLevel,
    position: BlockPos,
    light: Int,
    skyLight: Int,
    canSeeSky: Boolean,
    influences: MutableList<SpawningInfluence>,
    height: Int,
    nearbyBlocks: List<BlockState>,
    zone: SpawningZone,
    val attachments: Map<Direction, BlockState>
) : AreaSpawnablePosition(cause, world, position, light, skyLight, canSeeSky, influences, height, nearbyBlocks, zone) {
    fun attachmentHolder(direction: Direction): Holder<Block> =
        world.blockRegistry.wrapAsHolder(attachments.getValue(direction).block)

    override fun afterSpawn(action: SpawnAction<*>, entity: Entity) {
        super.afterSpawn(action, entity)
        if (entity !is PokemonEntity || !entity.pokemon.isWild()) return
        val condition = action.detail.conditions.filterIsInstance<ClingingTypeSpawningCondition<*>>().firstOrNull()
        val face = attachments.keys
            .filter { condition == null || condition.allowsAttachment(this, it) }
            .filter { direction ->
                val supportPos = entity.blockPosition().relative(direction)
                world.getBlockState(supportPos).isFaceSturdy(world, supportPos, direction.opposite)
            }
            .randomOrNull() ?: return
        entity.startClinging(face)
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
