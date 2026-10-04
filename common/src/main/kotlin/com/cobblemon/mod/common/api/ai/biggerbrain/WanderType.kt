/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

import com.cobblemon.mod.common.api.ai.CobblemonWanderControl.WanderCenter
import com.cobblemon.mod.common.api.ai.config.task.WanderTaskConfig.Companion.MAX_LOOK_DOWN_DISTANCE
import com.cobblemon.mod.common.api.ai.movement.FluidKind
import com.cobblemon.mod.common.api.ai.movement.MovementCapabilities
import com.cobblemon.mod.common.api.ai.movement.MovementIntent
import com.cobblemon.mod.common.api.ai.movement.Occupancy
import com.cobblemon.mod.common.api.ai.movement.TraversalPolicy
import com.cobblemon.mod.common.entity.OmniPathingEntity
import com.cobblemon.mod.common.entity.ai.CobblemonRandomSurfacePos
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import kotlin.math.ceil
import kotlin.math.sqrt
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.BehaviorUtils
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.HoverRandomPos
import net.minecraft.world.entity.ai.util.LandRandomPos
import net.minecraft.world.entity.ai.util.RandomPos
import net.minecraft.world.level.material.Fluids
import net.minecraft.world.level.pathfinder.PathType

/**
 * A type of wandering behaviour for an entity. I'm not sure if we keep this.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
enum class WanderType(
    val isValid: (PathfinderMob) -> Boolean,
    val choosePosition: (PathfinderMob, minimumHeight: Int, maximumHeight: Int, horizontalRange: Int, verticalRange: Int) -> BlockPos?,
    val createWalkTarget: (PathfinderMob, position: BlockPos, avoidsTargetingAir: Boolean, speedMultiplier: Float) -> WalkTarget?,
) {

    WATER(
        isValid = { it.isInWater },
        choosePosition = choosePosition@{ entity, _, _, horizontalRange, verticalRange ->
            var pos = BehaviorUtils.getRandomSwimmablePos(entity, horizontalRange, verticalRange)?.let(BlockPos::containing)
                ?: return@choosePosition null

            // Move target pos down so that we don't move out of water too much
            val requiredDepth = ceil(0.5 + entity.eyeHeight)
            // Get the current depth of the block
            var depth = 1
            var testPos = pos.above()
            var blockState = entity.level().getBlockState(testPos)
            while (blockState.fluidState.type == Fluids.WATER) {
                depth++
                testPos = testPos!!.above()
                blockState = entity.level().getBlockState(testPos)
            }

            if (depth < requiredDepth) {
                // Iterate down until we reach the desired min depth or we reach a non water block
                pos = pos.below()
                blockState = entity.level().getBlockState(pos)
                while (blockState.fluidState.type == Fluids.WATER && depth < requiredDepth && pos.y > entity.level().minBuildHeight) {
                    depth++
                    pos = pos.below()
                    blockState = entity.level().getBlockState(pos)
                }
            }

            return@choosePosition if (depth < requiredDepth) null else pos
        },
        createWalkTarget = { entity, pos, _, speedMultiplier ->
            CobblemonWalkTarget(
                pos = pos,
                nodeTypeFilter = { it == PathType.WATER || it == PathType.WATER_BORDER },
                speedModifier = speedMultiplier,
                completionRange = 0,
                // Stays swimming in water, wherever in the water it's comfortable or not.
                policy = TraversalPolicy.forIntent(entity, MovementIntent.IDLE) { occupancy, fluid ->
                    fluid == FluidKind.WATER && (occupancy == Occupancy.SWIM || occupancy == Occupancy.SWIM_SUBMERGED)
                }
            )
        }
    ),
    LAND(
        isValid = { entity -> !entity.isInWater && !entity.isInLava },
        choosePosition = choosePosition@{ entity, minimumHeight, maximumHeight, horizontalRange, verticalRange ->
            val targetVec = when {
                maximumHeight != -1 -> {
                    HoverRandomPos.getPos(
                        entity,
                        horizontalRange,
                        verticalRange,
                        entity.random.nextFloat() - 0.5,
                        entity.random.nextFloat() - 0.5,
                        Math.PI.toFloat(),
                        maximumHeight,
                        minimumHeight
                    )
                }
                entity.isUnderWater && entity.canBreatheUnderwater() && !((entity as OmniPathingEntity).canSwimInWater()) -> {
                    BehaviorUtils.getRandomSwimmablePos(
                        entity,
                        horizontalRange,
                        verticalRange
                    )
                }
                entity is OmniPathingEntity && (entity.canWalkOnWater() || entity.canWalkOnLava()) -> {
                    CobblemonRandomSurfacePos.getPos(
                        entity,
                        horizontalRange,
                        verticalRange
                    )
                }
                else -> {
                    LandRandomPos.getPos(
                        entity,
                        horizontalRange,
                        verticalRange
                    )
                }
            }

            val pos = targetVec?.let(BlockPos::containing) ?: return@choosePosition null
            LAND.applyHeightConstraints(pos, minimumHeight, maximumHeight, entity.level() as ServerLevel)
        },
        createWalkTarget = { entity, pos, avoidsTargetingAir, speedMultiplier ->
            CobblemonWalkTarget(
                pos = pos,
                speedModifier = speedMultiplier,
                completionRange = 0,
                nodeTypeFilter = { nodeType ->
                    entity.canBreatheUnderwater() || nodeType !in listOf(
                        PathType.WATER,
                        PathType.WATER_BORDER
                    )
                },
                destinationNodeTypeFilter = { nodeType ->
                    !avoidsTargetingAir || nodeType !in listOf(
                        PathType.OPEN
                    )
                },
                // Only goes through what it's comfortable with while idling, e.g. no swimming for non-breathers.
                policy = TraversalPolicy.forIntent(entity, MovementIntent.IDLE)
            )
        }
    ),
    /**
     * Seafloor walkers in water: they can't swim, but can breathe, so they wander along the bottom (or wade, or step
     * out onto land) instead. Neither [LAND] nor [WATER] applies to them while they're in water.
     */
    SEABED(
        isValid = { entity -> entity.isInWater && isSeafloorWalker(MovementCapabilities.of(entity)) },
        choosePosition = { entity, _, _, horizontalRange, verticalRange ->
            chooseBottomPosition(entity, horizontalRange, verticalRange)
        },
        createWalkTarget = { entity, pos, _, speedMultiplier ->
            CobblemonWalkTarget(
                pos = pos,
                speedModifier = speedMultiplier,
                completionRange = 0,
                policy = TraversalPolicy.forIntent(entity, MovementIntent.IDLE)
            )
        }
    )

    ;

    fun generateWalkTarget(
        entity: PathfinderMob,
        maxAttempts: Int,
        center: WanderCenter?,
        minimumHeight: Int,
        maximumHeight: Int,
        horizontalRange: Int,
        verticalRange: Int,
        avoidTargetingAir: Boolean,
        speedMultiplier: Float
    ): WalkTarget? {
        var attempts = 0
        var pos: BlockPos? = null

        while (attempts++ < maxAttempts && pos == null) {
            pos = choosePosition(entity, minimumHeight, maximumHeight, horizontalRange, verticalRange)
                ?: return null
            if (!isSuitable(center, pos)) {
                pos = null
            }
        }

        if (pos == null) {
            return null
        }

        return createWalkTarget(entity, pos, avoidTargetingAir, speedMultiplier)
    }

    private fun isSuitable(center: WanderCenter?, position: BlockPos): Boolean {
        val center = center ?: return true
        val centerPos = BlockPos(center.x.toInt(), center.y.toInt(), center.z.toInt())
        val distance = sqrt(centerPos.distSqr(position))
        val suitable = distance >= center.minRange && distance <= center.maxRange
        return suitable
    }

    private fun applyHeightConstraints(
        pos: BlockPos,
        minimumHeight: Int,
        maximumHeight: Int,
        world: ServerLevel
    ): BlockPos {
        if (minimumHeight <= 0 && maximumHeight == -1) {
            return pos // It ain't a hoverer
        }

        val block = world.getBlockState(pos)
        val altitude = if (!block.isAir) {
            0
        } else {
            (1..MAX_LOOK_DOWN_DISTANCE).firstOrNull {
                val newPos = pos.below(it)
                if (!world.getBlockState(newPos).isAir) {
                    return@firstOrNull true
                } else if (newPos.y <= world.minBuildHeight) {
                    // Don't adjust downward into the void (Mostly if we're in the end)
                    // This doesn't stop fliers from wandering off end islands,
                    // but it does stop them from diving directly into the void.
                    return pos
                }
                return@firstOrNull false
            } ?: MAX_LOOK_DOWN_DISTANCE
        }

        if (altitude > maximumHeight && maximumHeight >= 0) {
            val excess = altitude - maximumHeight
            val excessFromMinimum = altitude - minimumHeight
            val correction = world.random.nextIntBetweenInclusive(excess, excessFromMinimum)
            return pos.atY(pos.y - correction)
        } else if (altitude < minimumHeight && minimumHeight >= 0) {
            val deficit = minimumHeight - altitude
            val deficitFromMaximum = maximumHeight - altitude
            val correction = world.random.nextIntBetweenInclusive(deficit, deficitFromMaximum)
            return pos.atY(pos.y + correction)
        } else {
            return pos
        }
    }
}

/** Bodies that walk along the bottom of water rather than swimming in it, and can breathe down there. */
private fun isSeafloorWalker(caps: MovementCapabilities) = caps.canWalk && !caps.canSwimInWater && caps.canBreatheUnderwater

private const val BOTTOM_POSITION_SAMPLES = 10
private const val MAX_BOTTOM_DROP = 24

/**
 * Picks a random position the entity could stand at, settling sampled points onto the bottom the way a non-swimmer
 * would sink onto it. Only positions an idle policy allows are kept; reachability is the pathfinder's job.
 */
private fun chooseBottomPosition(entity: PathfinderMob, horizontalRange: Int, verticalRange: Int): BlockPos? {
    val level = entity.level()
    val policy = TraversalPolicy.forIntent(entity, MovementIntent.IDLE)
    val origin = entity.blockPosition()
    val tethering = (entity as? PokemonEntity)?.tethering
    repeat(BOTTOM_POSITION_SAMPLES) {
        var pos = origin.offset(RandomPos.generateRandomDirection(entity.random, horizontalRange, verticalRange))
        var climbed = 0
        while (level.getBlockState(pos).isSolid && climbed++ < verticalRange) {
            pos = pos.above()
        }
        var occupancy = Occupancy.at(policy.capabilities, entity.eyeHeight, pos, level::getBlockState)
        var dropped = 0
        while (occupancy?.isStandable == false && dropped++ < MAX_BOTTOM_DROP && pos.y > level.minBuildHeight) {
            pos = pos.below()
            occupancy = Occupancy.at(policy.capabilities, entity.eyeHeight, pos, level::getBlockState)
        }
        if (occupancy == null || pos == origin) {
            return@repeat
        } else if (policy.cost(occupancy, FluidKind.of(level.getFluidState(pos))) == null) {
            return@repeat
        } else if (tethering?.canRoamTo(pos) == false) {
            return@repeat
        }
        return pos
    }
    return null
}
