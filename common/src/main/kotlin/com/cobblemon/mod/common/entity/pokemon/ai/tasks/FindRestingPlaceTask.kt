/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.pokemon.ai.tasks

import com.cobblemon.mod.common.CobblemonActivities
import com.cobblemon.mod.common.CobblemonMemories
import com.cobblemon.mod.common.api.pokemon.status.Statuses
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.entity.pokemon.ai.PokemonMoveControl
import com.cobblemon.mod.common.pokemon.ai.SleepPlace
import com.cobblemon.mod.common.pokemon.status.PersistentStatusContainer
import com.cobblemon.mod.common.util.PositionType
import com.cobblemon.mod.common.util.blockRegistry
import com.cobblemon.mod.common.util.canEntityStayAt
import com.cobblemon.mod.common.util.getBlockPositions
import com.cobblemon.mod.common.util.getMemorySafely
import com.google.common.collect.ImmutableMap
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.ai.behavior.Behavior
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class FindRestingPlaceTask(
    private val horizontalSearchDistance: Int,
    private val verticalSearchDistance: Int
) : Behavior<PokemonEntity>(
    ImmutableMap.of(
        MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
        CobblemonMemories.POKEMON_DROWSY, MemoryStatus.VALUE_PRESENT,
        CobblemonMemories.PATH_COOLDOWN, MemoryStatus.VALUE_ABSENT
    ),
    600
) {
    private var targetPos = BlockPos.ZERO
    private var targetFace: Direction? = null
    private var targetPlace: SleepPlace? = null
    private var isAscending = false

    override fun checkExtraStartConditions(world: ServerLevel, entity: PokemonEntity): Boolean {
        if (entity.isBattling || entity.clingingFace != null || entity.isHangingFromThread) return false
        if (entity.pokemon.status?.status == Statuses.SLEEP) return false
        val target = findTarget(world, entity) ?: return false
        targetPos = target.first
        targetFace = target.second
        return true
    }

    override fun start(world: ServerLevel, entity: PokemonEntity, time: Long) {
        entity.isSettlingToSleep = true
        isAscending = false
        entity.brain.setMemoryWithExpiry(CobblemonMemories.PATH_COOLDOWN, true, 80)
    }

    override fun canStillUse(world: ServerLevel, entity: PokemonEntity, time: Long): Boolean {
        return entity.isSettlingToSleep &&
                entity.pokemon.status?.status != Statuses.SLEEP &&
                entity.brain.getMemorySafely(CobblemonMemories.POKEMON_DROWSY).orElse(false)
    }

    override fun tick(world: ServerLevel, entity: PokemonEntity, time: Long) {
        if (isAscending) {
            tickAscent(world, entity)
            return
        }
        if (targetFace == null) {
            tickGroundApproach(entity)
            return
        }
        if (!hasArrived(entity)) {
            walkTowardsTarget(entity, completionRange = 1)
            return
        }
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        when (targetFace) {
            Direction.UP -> beginAscent(world, entity)
            else -> settleAgainstWall(world, entity)
        }
    }

    private fun tickGroundApproach(entity: PokemonEntity) {
        if (entity.canSleepAt(entity.blockPosition().below()) || entity.blockPosition() == targetPos) {
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.isSettlingToSleep = false
            return
        }
        walkTowardsTarget(entity, completionRange = 0)
    }

    private fun walkTowardsTarget(entity: PokemonEntity, completionRange: Int) {
        if (!entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(targetPos, WALK_SPEED, completionRange))
        }
    }

    override fun stop(world: ServerLevel, entity: PokemonEntity, time: Long) {
        entity.isSettlingToSleep = false
        if (isAscending && entity.clingingFace == null) {
            entity.isNoGravity = false
        }
    }

    private fun hasArrived(entity: PokemonEntity): Boolean {
        val dx = targetPos.x + 0.5 - entity.x
        val dz = targetPos.z + 0.5 - entity.z
        val horizontalSqr = dx * dx + dz * dz
        if (targetFace == Direction.UP) return horizontalSqr <= ARRIVAL_DISTANCE_SQR
        val dy = targetPos.y + 0.5 - entity.y
        return horizontalSqr + dy * dy <= ARRIVAL_DISTANCE_SQR
    }

    private fun beginAscent(world: ServerLevel, entity: PokemonEntity) {
        if (!bodyFits(world, entity, targetPos.x + 0.5, entity.y, targetPos.z + 0.5)) {
            walkTowardsTarget(entity, completionRange = 0)
            return
        }
        isAscending = true
        entity.isNoGravity = true
        haltSteering(entity)
        entity.setPos(targetPos.x + 0.5, entity.y, targetPos.z + 0.5)
        if (entity.behaviour.moving.fly.canFly) {
            entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
        }
    }

    /** The move control keeps steering towards its last wanted position even after the navigation
     * stops and it ticks after the brain so we have to halt it otehrwise it overwrites the climb speed. */
    private fun haltSteering(entity: PokemonEntity) {
        entity.navigation.stop()
        (entity.moveControl as? PokemonMoveControl)?.stop()
    }

    private fun tickAscent(world: ServerLevel, entity: PokemonEntity) {
        entity.isNoGravity = true
        haltSteering(entity)
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.setPos(targetPos.x + 0.5, entity.y, targetPos.z + 0.5)

        if (!isCeilingFor(world, targetPos.above(), targetPlace) || isSpotClaimed(world, entity, targetPos)) {
            entity.isSettlingToSleep = false
            return
        }
        val headY = targetPos.y + 1.0
        val gap = headY - (entity.y + entity.bbHeight)
        if (gap <= 0.05) {
            entity.setPos(targetPos.x + 0.5, headY - entity.bbHeight, targetPos.z + 0.5)
            entity.setDeltaMovement(Vec3.ZERO)
            settle(entity, Direction.UP)
        } else {
            entity.setDeltaMovement(0.0, min(0.15, gap), 0.0)
        }
    }

    private fun settleAgainstWall(world: ServerLevel, entity: PokemonEntity) {
        val face = targetFace ?: return
        val place = targetPlace
        if (place == null || !wallFits(world, entity, place, targetPos, face) || isSpotClaimed(world, entity, targetPos, claimSpacing(face))) {
            entity.isSettlingToSleep = false
            return
        }
        entity.setPos(targetPos.x + 0.5, targetPos.y.toDouble(), targetPos.z + 0.5)
        settle(entity, face)
    }

    private fun settle(entity: PokemonEntity, face: Direction) {
        entity.startClinging(face)
        entity.isSleepClinging = true
        entity.isSettlingToSleep = false
        if (entity.pokemon.status?.status != Statuses.SLEEP) {
            entity.pokemon.status = PersistentStatusContainer(Statuses.SLEEP)
            entity.brain.setMemory(CobblemonMemories.POKEMON_SLEEPING, true)
            entity.brain.setActiveActivityToFirstValid(listOf(CobblemonActivities.POKEMON_SLEEPING_ACTIVITY))
        }
    }

    private fun findTarget(world: ServerLevel, entity: PokemonEntity): Pair<BlockPos, Direction?>? {
        val searchBox = AABB.ofSize(entity.position(), horizontalSearchDistance.toDouble(), verticalSearchDistance.toDouble(), horizontalSearchDistance.toDouble())
        for (place in entity.behaviour.resting.places) {
            val found = searchPlace(world, entity, place, searchBox)
            if (found != null) {
                targetPlace = place
                return found
            }
        }
        targetPlace = null
        val ground = world.getBlockPositions(searchBox)
            .filter(entity::canSleepAt)
            .minByOrNull { it.distToCenterSqr(entity.position()) }
        return ground?.let { it.above() to null }
    }

    private fun searchPlace(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, searchBox: AABB): Pair<BlockPos, Direction?>? {
        val spots = mutableListOf<Pair<BlockPos, Direction>>()
        if (place.allowsCeiling()) spots += ceilingSpots(world, entity, place, searchBox)
        if (place.allowsSides()) spots += wallSpots(world, entity, place, searchBox)
        if (place.allowsFloor()) spots += floorSpots(world, entity, place, searchBox)
        val match = spots
            .filter { (spot, face) -> fitsPlaceSpace(world, entity, place, spot) && !isSpotClaimed(world, entity, spot, claimSpacing(face)) }
            .minByOrNull { (spot, _) -> spot.distToCenterSqr(entity.position()) }
        return match?.let { (spot, face) -> spot to face.takeUnless { it == Direction.DOWN } }
    }

    /** The spot for each ceiling is the air block directly below it, however high above the floor.
     * The hanging body needs clearance for its full width and height or a large mon would clip
     * into the surrounding blocks */
    private fun ceilingSpots(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, searchBox: AABB): List<Pair<BlockPos, Direction>> =
        world.getBlockPositions(searchBox)
            .filter { world.getBlockState(it).isAir }
            .mapNotNull { pos -> findCeilingAbove(world, pos, place) }
            .distinct()
            .filter { ceiling ->
                val minY = ceiling.y - entity.bbHeight + entity.behaviour.clinging.yOffset
                bodyFits(world, entity, ceiling.x + 0.5, minY, ceiling.z + 0.5)
            }
            .map { it.below() to Direction.UP }

    private fun bodyFits(world: ServerLevel, entity: PokemonEntity, centerX: Double, minY: Double, centerZ: Double): Boolean {
        val half = entity.bbWidth / 2.0
        val box = AABB(centerX - half, minY, centerZ - half, centerX + half, minY + entity.bbHeight, centerZ + half)
        return world.noCollision(entity, box)
    }

    /** Fliers may cling anywhere along a wall but want open air below so they perch above the
     * ground; walkers only cling where they can stand beside the wall. Might need tweaked so that we add a check for an air block above too? we will see */
    private fun wallSpots(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, searchBox: AABB): List<Pair<BlockPos, Direction>> {
        val spots = if (entity.behaviour.moving.fly.canFly) {
            world.getBlockPositions(searchBox).filter { pos ->
                val below = pos.below()
                world.getBlockState(pos).isAir && world.getBlockState(below).getCollisionShape(world, below).isEmpty
            }
        } else {
            val width = ceil(entity.bbWidth).toInt()
            val height = ceil(entity.bbHeight).toInt()
            world.getBlockPositions(searchBox)
                .filter { world.canEntityStayAt(it, width, height, PositionType.LAND) }
                .map { it.above() }
        }
        val xOffset = entity.behaviour.clinging.xOffset
        return spots.mapNotNull { spot ->
            Direction.Plane.HORIZONTAL.firstOrNull { direction ->
                wallFits(world, entity, place, spot, direction) &&
                        bodyFits(world, entity, spot.x + 0.5 + direction.stepX * xOffset, spot.y.toDouble(), spot.z + 0.5 + direction.stepZ * xOffset)
            }?.let { spot to it }
        }
    }

    /** The wall must be sturdy across the mon's whole projected span, or it would pick lone
     * blocks far smaller than its body and hang off their edges. The vertical span uses the
     * larger dimension because the fallback cling pose stands the model up along the wall */
    private fun wallFits(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, spot: BlockPos, direction: Direction): Boolean {
        val verticalSpan = ceil(max(entity.bbWidth, entity.bbHeight)).toInt().coerceAtLeast(1)
        val horizontalSpan = ceil(entity.bbWidth - 0.2).toInt().coerceAtLeast(1)
        val sideways = direction.clockWise
        for (up in 0 until verticalSpan) {
            for (side in 0 until horizontalSpan) {
                val wallPos = spot.above(up).relative(direction).relative(sideways, side - (horizontalSpan - 1) / 2)
                val state = world.getBlockState(wallPos)
                if (!state.isFaceSturdy(world, wallPos, direction.opposite) || !placeBlockMatches(world, place, state)) {
                    return false
                }
            }
        }
        return true
    }

    private fun floorSpots(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, searchBox: AABB): List<Pair<BlockPos, Direction>> =
        world.getBlockPositions(searchBox)
            .filter { entity.canSleepAt(it) && placeBlockMatches(world, place, world.getBlockState(it)) }
            .map { it.above() to Direction.DOWN }

    private fun fitsPlaceSpace(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, spot: BlockPos): Boolean {
        val state = world.getBlockState(spot)
        if (place.onlyAir && !state.isAir) return false
        val rest = entity.behaviour.resting
        val light = place.light ?: rest.light
        if (world.getMaxLocalRawBrightness(spot) !in light) return false
        val canSeeSky = place.canSeeSky ?: rest.canSeeSky
        if (canSeeSky != null && world.canSeeSky(spot.above()) != canSeeSky) return false
        val skyLight = place.skyLight ?: rest.skyLight
        return skyLight == null || world.lightEngine.getLayerListener(LightLayer.SKY).getLightValue(spot) in skyLight
    }

    private fun findCeilingAbove(world: ServerLevel, from: BlockPos, place: SleepPlace?): BlockPos? {
        for (offset in 1..MAX_ASCENT) {
            val pos = from.above(offset)
            if (world.getBlockState(pos).getCollisionShape(world, pos).isEmpty) continue
            return pos.takeIf { isCeilingFor(world, pos, place) }
        }
        return null
    }

    /** A place that whitelists blocks may attach to not sturdy undersides like leaves;
     * without a whitelist only sturdy undersides count */
    private fun isCeilingFor(world: ServerLevel, pos: BlockPos, place: SleepPlace?): Boolean {
        val state = world.getBlockState(pos)
        if (state.getCollisionShape(world, pos).isEmpty) return false
        if (place != null && place.blocks.isNotEmpty()) return placeBlockMatches(world, place, state)
        return state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.DOWN)
    }

    private fun placeBlockMatches(world: ServerLevel, place: SleepPlace, state: BlockState): Boolean =
        place.blocks.isEmpty() || place.blocks.any { it.fits(state.block, world.blockRegistry) }

    /** Wall clingers keep an air block of spacing so they don't overlap along the same surface;
     * ceiling sleepers may pack together. */
    private fun claimSpacing(face: Direction) = if (face.axis.isHorizontal) 1.0 else 0.0

    private fun isSpotClaimed(world: ServerLevel, entity: PokemonEntity, pos: BlockPos, spacing: Double = 0.0): Boolean {
        return world.getEntitiesOfClass(PokemonEntity::class.java, AABB(pos).inflate(spacing)) {
            it !== entity && (it.clingingFace != null || it.isSettlingToSleep)
        }.isNotEmpty()
    }

    companion object {
        fun create(horizontalSearchDistance: Int, verticalSearchDistance: Int) = FindRestingPlaceTask(horizontalSearchDistance, verticalSearchDistance)

        private const val MAX_ASCENT = 20
        private const val ARRIVAL_DISTANCE_SQR = 4.0
        private const val WALK_SPEED = 0.35F
    }
}