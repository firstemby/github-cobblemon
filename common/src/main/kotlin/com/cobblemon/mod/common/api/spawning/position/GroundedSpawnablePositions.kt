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
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
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
import net.minecraft.world.level.EmptyBlockGetter
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
    override fun postFilter(detail: SpawnDetail): Boolean {
        val sizeX = detail.width.takeIf { it > 0 } ?: 1
        val sizeY = detail.height.takeIf { it > 0 } ?: 1
        if (sizeX <= 1 && sizeY <= 1) return true

        val monPos = position.above()
        val anchorOffset = (1..SilkThreadBlock.MAX_LENGTH).firstOrNull { offset ->
            SilkThreadBlock.isValidAnchor(zone.getBlockState(monPos.above(offset)))
        } ?: return false
        val anchorY = monPos.y + anchorOffset
        val radius = sizeX / 2 + 1

        val mutable = BlockPos.MutableBlockPos()
        for (x in (position.x - radius)..(position.x + radius)) {
            for (z in (position.z - radius)..(position.z + radius)) {
                if (!SilkThreadBlock.isValidAnchor(zone.getBlockState(mutable.set(x, anchorY, z)))) {
                    return false
                }
                for (y in (anchorY - sizeY) until anchorY) {
                    if (zone.getBlockState(mutable.set(x, y, z)).isSolid) {
                        return false
                    }
                }
            }
        }
        return true
    }

    override fun afterSpawn(action: SpawnAction<*>, entity: Entity) {
        super.afterSpawn(action, entity)
        if (entity !is PokemonEntity || !entity.pokemon.isWild()) return
        if (entity.behaviour.hanging.usesThread) {
            if (placeThreadColumn(entity.blockPosition())) {
                entity.startHangingFromThread()
            }
        } else {
            snapToAnchor(entity)
        }
    }

    private fun snapToAnchor(entity: PokemonEntity) {
        val monPos = entity.blockPosition()
        val anchorOffset = (1..SilkThreadBlock.MAX_LENGTH).firstOrNull { offset ->
            SilkThreadBlock.isValidAnchor(world.getBlockState(monPos.above(offset)))
        } ?: return
        val anchor = monPos.above(anchorOffset)
        entity.setPos(monPos.x + 0.5, anchor.y - entity.bbHeight + entity.behaviour.hanging.yOffset, monPos.z + 0.5)
        entity.startHangingFromThread()
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

    override fun postFilter(detail: SpawnDetail): Boolean {
        val sizeX = detail.width.takeIf { it > 0 } ?: 1
        val sizeY = detail.height.takeIf { it > 0 } ?: 1
        if (sizeX <= 1 && sizeY <= 1) return true
        return attachments.keys.any { hasFootprintSpace(it, sizeX, sizeY) }
    }

    // attempt at trying to make sure it has space for the mon
    private fun hasFootprintSpace(face: Direction, sizeX: Int, sizeY: Int): Boolean {
        val monPos = position.above()
        val mutable = BlockPos.MutableBlockPos()
        if (face == Direction.UP) {
            val radius = sizeX / 2 + 1
            val ceilingY = monPos.y + 1
            for (x in (monPos.x - radius)..(monPos.x + radius)) {
                for (z in (monPos.z - radius)..(monPos.z + radius)) {
                    val ceilingState = zone.getBlockState(mutable.set(x, ceilingY, z))
                    if (!ceilingState.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.DOWN)) {
                        return false
                    }
                    for (y in (ceilingY - sizeY) until ceilingY) {
                        if (zone.getBlockState(mutable.set(x, y, z)).isSolid) {
                            return false
                        }
                    }
                }
            }
            return true
        }
        if (!face.axis.isHorizontal) return false

        val along = face.clockWise
        val radius = sizeX / 2
        for (a in -radius..radius) {
            for (up in 0 until sizeX) {
                val wallPos = monPos.relative(face).relative(along, a).above(up)
                if (!zone.getBlockState(mutable.set(wallPos)).isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face.opposite)) {
                    return false
                }
                for (depth in 0 until sizeY) {
                    val bodyPos = wallPos.relative(face.opposite, depth + 1)
                    if (zone.getBlockState(mutable.set(bodyPos)).isSolid) {
                        return false
                    }
                }
            }
        }
        return true
    }

    override fun afterSpawn(action: SpawnAction<*>, entity: Entity) {
        super.afterSpawn(action, entity)
        if (entity !is PokemonEntity || !entity.pokemon.isWild()) return
        val condition = action.detail.conditions.filterIsInstance<ClingingTypeSpawningCondition<*>>().firstOrNull()
        val face = attachments.keys
            .filter { condition == null || condition.allowsAttachment(this, it) }
            .filter { direction ->
                val supportPos = entity.blockPosition().relative(direction)
                val state = world.getBlockState(supportPos)
                state.isFaceSturdy(world, supportPos, direction.opposite) && entity.canClingTo(direction, state)
            }
            .randomOrNull()
        if (face == null) {
            entity.discard()
            return
        }
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