/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.position.calculators

import com.cobblemon.mod.common.Cobblemon.config
import com.cobblemon.mod.common.api.spawning.position.*
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.isAirCondition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.isLavaCondition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.isSolidCondition
import com.cobblemon.mod.common.api.spawning.position.calculators.SpawnablePositionCalculator.Companion.isWaterCondition
import com.cobblemon.mod.common.block.SilkThreadBlock
import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

/**
 * A spawnable position calculator that creates some kind of [FlooredSpawnablePosition]. The shared
 * idea of these spawnable positions is that there is a base block condition for the floor, and then some
 * other condition for its surroundings.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
interface FlooredSpawnablePositionCalculator<T : FlooredSpawnablePosition> : AreaSpawnablePositionCalculator<T> {
    /** The condition that must be met by the base block. */
    val baseCondition: (BlockState) -> Boolean
    /** The condition that must be met by the surrounding blocks. */
    val surroundingCondition: (BlockState) -> Boolean

    override fun fits(input: AreaSpawningInput): Boolean {
        val floorState = input.zone.getBlockState(input.position)
        val aboveState = input.zone.getBlockState(input.position.above())
        return baseCondition(floorState) && surroundingCondition(aboveState)
    }
}

/**
 * The spawnable position calculator used for [GroundedSpawnablePosition]s. Requires a solid block below it and
 * air blocks in its surroundings.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
object GroundedSpawnablePositionCalculator : FlooredSpawnablePositionCalculator<GroundedSpawnablePosition> {
    override val name = "grounded"
    override val baseCondition: (BlockState) -> Boolean = isSolidCondition
    override val surroundingCondition: (BlockState) -> Boolean = isAirCondition

    override fun calculate(input: AreaSpawningInput): GroundedSpawnablePosition {
        return GroundedSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = getHeight(input, surroundingCondition, config.maxVerticalSpace, offsetY = 1),
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input)
        )
    }
}

/**
 * The spawnable position calculator used for [HangingSpawnablePosition]s. Requires an air pocket
 * beneath leaves, with at most [SilkThreadBlock.MAX_LENGTH] air blocks of thread gap between them.
 * SilkThreadBlock currently only accepts leaves.... but maybe if we find other mons that hang from
 * string we can expand it to a tag json (might maybe it should be anyways....) or expand it to a
 * system where things can hang without string and use this same method (like bats maybe)
 */
object HangingSpawnablePositionCalculator : AreaSpawnablePositionCalculator<HangingSpawnablePosition> {
    override val name = "hanging"

    override fun fits(input: AreaSpawningInput): Boolean {
        if (!isAirCondition(input.zone.getBlockState(input.position))) return false
        if (!isAirCondition(input.zone.getBlockState(input.position.above()))) return false
        for (offset in 2..SilkThreadBlock.MAX_LENGTH + 1) {
            val state = input.zone.getBlockState(input.position.above(offset))
            when {
                SilkThreadBlock.isValidAnchor(state) -> return true
                !isAirCondition(state) -> return false
            }
        }
        return false
    }

    override fun calculate(input: AreaSpawningInput): HangingSpawnablePosition {
        return HangingSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = 1,
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input)
        )
    }
}

/**
 * The spawnable position calculator used for [ClingingSpawnablePosition]s. Requires an air block
 * for the Pokemon with at least one solid neighbouring block to attach to.
 */
object ClingingSpawnablePositionCalculator : AreaSpawnablePositionCalculator<ClingingSpawnablePosition> {
    override val name = "clinging"

    override fun fits(input: AreaSpawningInput): Boolean {
        if (!isAirCondition(input.zone.getBlockState(input.position.above()))) return false
        return findAttachments(input).isNotEmpty()
    }

    override fun calculate(input: AreaSpawningInput): ClingingSpawnablePosition {
        return ClingingSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = 1,
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input),
            attachments = findAttachments(input)
        )
    }

    private fun findAttachments(input: AreaSpawningInput): Map<Direction, BlockState> {
        val monPos = input.position.above()
        return Direction.entries
            .map { it to input.zone.getBlockState(monPos.relative(it)) }
            .filter { (_, state) -> isSolidCondition(state) }
            .toMap()
    }
}

/**
 * The spawnable position calculator used for [SeafloorSpawnablePosition]s. Requires a solid block below it and
 * water blocks in its surroundings.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
object SeafloorSpawnablePositionCalculator : FlooredSpawnablePositionCalculator<SeafloorSpawnablePosition> {
    override val name = "seafloor"
    override val baseCondition: (BlockState) -> Boolean = isSolidCondition
    override val surroundingCondition: (BlockState) -> Boolean = isWaterCondition

    override fun calculate(input: AreaSpawningInput): SeafloorSpawnablePosition {
        return SeafloorSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = getHeight(input, surroundingCondition, config.maxVerticalSpace, offsetY = 1),
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input)
        )
    }
}

/**
 * The spawnable position calculator used for [LavafloorSpawnablePosition]s. Requires a solid block below it and
 * lava blocks in its surroundings.
 *
 * @author Hiroku
 * @since February 7th, 2022
 */
object LavafloorSpawnablePositionCalculator : FlooredSpawnablePositionCalculator<LavafloorSpawnablePosition> {
    override val name = "lavafloor"
    override val baseCondition: (BlockState) -> Boolean = isSolidCondition
    override val surroundingCondition: (BlockState) -> Boolean = isLavaCondition

    override fun calculate(input: AreaSpawningInput): LavafloorSpawnablePosition {
        return LavafloorSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = getHeight(input, surroundingCondition, config.maxVerticalSpace, offsetY = 1),
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input)
        )
    }
}


/**
 * The spawnable position calculator used for [SurfaceSpawnablePosition]s. Requires a fluid block below it and
 * air blocks in its surroundings.
 *
 * @author Hiroku
 * @since December 15th, 2022
 */
object SurfaceSpawnablePositionCalculator : FlooredSpawnablePositionCalculator<SurfaceSpawnablePosition> {
    override val name = "surface"
    override val baseCondition: (BlockState) -> Boolean = { !it.fluidState.isEmpty }
    override val surroundingCondition: (BlockState) -> Boolean = isAirCondition

    override fun calculate(input: AreaSpawningInput): SurfaceSpawnablePosition {
        return SurfaceSpawnablePosition(
            cause = input.cause,
            world = input.world,
            position = input.position.immutable(),
            light = getLight(input),
            skyLight = getSkyLight(input),
            canSeeSky = getCanSeeSky(input),
            influences = input.spawner.copyInfluences(),
            height = getHeight(input, surroundingCondition, config.maxVerticalSpace / 2, offsetY = 1),
            depth = getDepth(input, baseCondition, config.maxVerticalSpace / 2),
            zone = input.zone,
            nearbyBlocks = getNearbyBlocks(input)
        )
    }
}

