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
import com.cobblemon.mod.common.entity.pokemon.ai.SleepDebug
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
import net.minecraft.util.Mth
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
    private var stallTicks = 0
    private var lastApproachPos = Vec3.ZERO
    private var failedSpot: BlockPos? = null
    private var failedSpotExpiry = 0L

    override fun checkExtraStartConditions(world: ServerLevel, entity: PokemonEntity): Boolean {
        if (entity.isBattling || entity.clingingFace != null || entity.isHangingFromThread) return false
        if (entity.pokemon.status?.status == Statuses.SLEEP) {
            if (world.gameTime % 40 == 0L) SleepDebug.log(entity, "task blocked: already asleep")
            return false
        }
        // Navigation refuses to compute paths for airborne mons that arent flagged as flying so
        // the search waits for a pathable moment and looks for valid spots
        if (!entity.onGround() && !entity.isInWater && !entity.getBehaviourFlag(PokemonBehaviourFlag.FLYING)) return false
        if ((world.gameTime + entity.id) % SEARCH_INTERVAL != 0L) return false
        val target = findTarget(world, entity)
        if (target == null) {
            SleepDebug.log(entity, "no resting spot found")
            return false
        }
        SleepDebug.log(entity, "chose spot=${target.first.toShortString()} face=${target.second ?: "ground"}")
        targetPos = target.first
        targetFace = target.second
        return true
    }

    override fun start(world: ServerLevel, entity: PokemonEntity, time: Long) {
        SleepDebug.log(entity, "task start target=${targetPos.toShortString()} face=${targetFace ?: "ground"}")
        entity.isSettlingToSleep = true
        stallTicks = 0
        lastApproachPos = entity.position()
        entity.brain.setMemoryWithExpiry(CobblemonMemories.PATH_COOLDOWN, true, 80)
    }

    override fun canStillUse(world: ServerLevel, entity: PokemonEntity, time: Long): Boolean {
        return entity.isSettlingToSleep &&
                entity.pokemon.status?.status != Statuses.SLEEP &&
                entity.brain.getMemorySafely(CobblemonMemories.POKEMON_DROWSY).orElse(false)
    }

    override fun tick(world: ServerLevel, entity: PokemonEntity, time: Long) {
        if (targetFace == null) {
            tickGroundApproach(entity)
            return
        }
        if (targetFace == Direction.UP) {
            tickCeilingApproach(world, entity)
            return
        }
        if (!hasArrived(entity)) {
            walkTowardsTarget(entity, completionRange = 1)
            return
        }
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        settleAgainstWall(world, entity)
    }

    /** Mons will try to get to the air block below the ceiling and if they are large they might need a little shift into it to get the task running */
    private fun tickCeilingApproach(world: ServerLevel, entity: PokemonEntity) {
        if (!isValidCeiling(world, targetPos.above(), targetPlace) || isSpotClaimed(world, entity, targetPos)) {
            SleepDebug.log(entity, "ceiling approach aborted at ${targetPos.toShortString()}: ceiling gone or spot claimed")
            entity.isSettlingToSleep = false
            return
        }
        // A mon who can not progress should ideely wait for a bit to check for any new spots
        stallTicks = if (entity.position().distanceToSqr(lastApproachPos) < 0.001) stallTicks + 1 else 0
        lastApproachPos = entity.position()
        if (stallTicks > STALL_GIVE_UP_TICKS) {
            SleepDebug.log(entity, "approach stalled, shunning ${targetPos.toShortString()}")
            failedSpot = targetPos
            failedSpotExpiry = world.gameTime + FAILED_SPOT_MEMORY_TICKS
            entity.isSettlingToSleep = false
            return
        }
        val dx = targetPos.x + 0.5 - entity.x
        val dz = targetPos.z + 0.5 - entity.z
        val horizontalSqr = dx * dx + dz * dz
        val isAtColumn = horizontalSqr <= COLUMN_RADIUS_SQR
        if (world.gameTime % 10 == 0L) {
            SleepDebug.log(entity, "ceiling approach target=${targetPos.toShortString()}" +
                    " dx=%.2f dz=%.2f headGap=%.2f".format(dx, dz, targetPos.y + 1.0 - (entity.y + entity.bbHeight)) +
                    " atColumn=$isAtColumn ceilingAboveHead=${isUnderTargetCeiling(world, entity)} navDone=${entity.navigation.isDone}" +
                    " spotBlock=${world.getBlockState(targetPos).block.name.string}" +
                    " ceilingBlock=${world.getBlockState(targetPos.above()).block.name.string}" +
                    " feetPosBlock=${world.getBlockState(hangPos(entity)).block.name.string}")
        }
        if (isAtColumn && entity.y + entity.bbHeight >= targetPos.y && isUnderTargetCeiling(world, entity)) {
            SleepDebug.log(entity, "grabbing ceiling at ${targetPos.toShortString()}")
            entity.setDeltaMovement(Vec3.ZERO)
            settle(entity, Direction.UP)
            return
        }
        // try to nudge to the air space below the ceiling here, but rather than nudge them we might need to check a radius from the tardet area instead and snap to that location?
        // Currently this looks smoother.... but we might need to do more testing.
        if (horizontalSqr <= DRIFT_RADIUS_SQR && entity.navigation.isDone) {
            if (world.gameTime % 10 == 0L) SleepDebug.log(entity, "nav stalled near column, drifting in")
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            haltSteering(entity)
            if (entity.behaviour.moving.fly.canFly) {
                entity.setBehaviourFlag(PokemonBehaviourFlag.FLYING, true)
            }
            val remaining = targetPos.y + 1.0 - (entity.y + entity.bbHeight)
            entity.setDeltaMovement(dx * 0.1, min(0.15, remaining), dz * 0.1)
            return
        }
        walkTowardsTarget(entity, completionRange = 0, pos = hangPos(entity))
    }

    /** Block position of where our mons feets will be when hanging from and wanting to go towards */
    private fun hangPos(entity: PokemonEntity, spot: BlockPos = targetPos): BlockPos =
        BlockPos.containing(spot.x + 0.5, spot.y + 1.0 - entity.bbHeight, spot.z + 0.5)

    /** Try to get a read on where the head of the mon is based on the target ceiling spot for knowing when to stop */
    private fun isUnderTargetCeiling(world: ServerLevel, entity: PokemonEntity): Boolean {
        val headPos = BlockPos.containing(entity.x, entity.y + entity.bbHeight, entity.z)
        return findCeilingAbove(world, headPos.below(), targetPlace) == targetPos.above()
    }

    private fun tickGroundApproach(entity: PokemonEntity) {
        if (entity.canSleepAt(entity.blockPosition().below()) || entity.blockPosition() == targetPos) {
            SleepDebug.log(entity, "ground approach done at ${entity.blockPosition().toShortString()}")
            entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
            entity.isSettlingToSleep = false
            return
        }
        walkTowardsTarget(entity, completionRange = 0)
    }

    private fun walkTowardsTarget(entity: PokemonEntity, completionRange: Int, pos: BlockPos = targetPos) {
        if (!entity.brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            SleepDebug.log(entity, "setting walk target ${pos.toShortString()} range=$completionRange")
            entity.brain.setMemory(MemoryModuleType.WALK_TARGET, WalkTarget(pos, WALK_SPEED, completionRange))
        }
    }

    override fun stop(world: ServerLevel, entity: PokemonEntity, time: Long) {
        entity.isSettlingToSleep = false
    }

    private fun hasArrived(entity: PokemonEntity): Boolean {
        val dx = targetPos.x + 0.5 - entity.x
        val dz = targetPos.z + 0.5 - entity.z
        val dy = targetPos.y + 0.5 - entity.y
        return dx * dx + dz * dz + dy * dy <= ARRIVAL_DISTANCE_SQR
    }

    /** The move control keeps steering towards its last wanted position even after the navigation
     * stops and it ticks after the brain so we have to stop it otehrwise it overwrites the nudging into placement */
    private fun haltSteering(entity: PokemonEntity) {
        entity.navigation.stop()
        (entity.moveControl as? PokemonMoveControl)?.stop()
    }

    private fun settleAgainstWall(world: ServerLevel, entity: PokemonEntity) {
        val face = targetFace ?: return
        val place = targetPlace
        if (place == null || !wallFits(world, entity, place, targetPos, face) || isSpotClaimed(world, entity, targetPos, claimSpacing(face))) {
            SleepDebug.log(entity, "wall settle aborted at ${targetPos.toShortString()}: wall gone or spot claimed")
            entity.isSettlingToSleep = false
            return
        }
        entity.setPos(targetPos.x + 0.5, targetPos.y.toDouble(), targetPos.z + 0.5)
        settle(entity, face)
    }

    private fun settle(entity: PokemonEntity, face: Direction) {
        SleepDebug.log(entity, "settled on face=$face at ${targetPos.toShortString()}")
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
        entity.brain.setMemoryWithExpiry(CobblemonMemories.POKEMON_SLEEP_SEARCH_FAILED, true, PLACE_SEARCH_FAILURE_TICKS)
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
        SleepDebug.log(entity, "place faces=${place.faces} spots=${spots.size}")
        // try to do some lazy checks on valid spots
        var pathChecks = 0
        val match = spots
            .sortedBy { (spot, _) -> spot.distToCenterSqr(entity.position()) }
            .asSequence()
            .filter { (spot, face) ->
                if (spot == failedSpot && world.gameTime < failedSpotExpiry) return@filter false
                val hasBodyRoom = face != Direction.UP || bodyFits(
                    world, entity, spot.x + 0.5,
                    spot.y + 1.0 - entity.bbHeight + entity.behaviour.clinging.yOffset, spot.z + 0.5
                )
                hasBodyRoom && fitsPlaceSpace(world, entity, place, spot) &&
                        !isSpotClaimed(world, entity, spot, claimSpacing(face))
            }
            .takeWhile { pathChecks < REACHABILITY_CHECKS }
            .firstOrNull { (spot, face) ->
                pathChecks++
                val navPos = if (face == Direction.UP) hangPos(entity, spot) else spot
                val path = entity.navigation.createPath(navPos, 0)
                val endPos = path?.endNode?.asBlockPos()
                SleepDebug.log(entity, "candidate ${spot.toShortString()} face=$face navPos=${navPos.toShortString()}" +
                        " path=${path?.let { p -> "${if (p.canReach()) "reaches" else "partial"} end=${endPos?.toShortString()} endDistSqr=${endPos?.distSqr(navPos)}" } ?: "null"}")
                path != null
            }
        return match?.let { (spot, face) -> spot to face.takeUnless { it == Direction.DOWN } }
    }

    /** Upwawrd scans from each area scan around the mon looking for open air spots with solid
     * blocks above them, but ignoring ones below floors stood on to make it have an easier time*/
    private fun ceilingSpots(world: ServerLevel, entity: PokemonEntity, place: SleepPlace, searchBox: AABB): List<Pair<BlockPos, Direction>> {
        val spots = mutableListOf<Pair<BlockPos, Direction>>()
        val feetY = entity.blockPosition().y
        val boxTop = Mth.floor(searchBox.maxY)
        val pos = BlockPos.MutableBlockPos()
        for (x in Mth.floor(searchBox.minX)..Mth.floor(searchBox.maxX)) {
            for (z in Mth.floor(searchBox.minZ)..Mth.floor(searchBox.maxZ)) {
                var seenAirInBox = false
                for (y in Mth.floor(searchBox.minY)..(boxTop + MAX_ASCENT)) {
                    pos.set(x, y, z)
                    val state = world.getBlockState(pos)
                    if (state.getCollisionShape(world, pos).isEmpty) {
                        if (y <= boxTop && state.isAir) seenAirInBox = true
                        continue
                    }
                    if (seenAirInBox && y >= feetY && isValidCeiling(world, pos, place)) {
                        spots += BlockPos(x, y - 1, z) to Direction.UP
                    }
                    seenAirInBox = false
                }
            }
        }
        SleepDebug.log(entity, "ceilings=${spots.size}")
        return spots
    }

    private fun bodyFits(world: ServerLevel, entity: PokemonEntity, centerX: Double, minY: Double, centerZ: Double): Boolean {
        val half = entity.bbWidth / 2.0
        val box = AABB(centerX - half, minY, centerZ - half, centerX + half, minY + entity.bbHeight, centerZ + half)
        return world.noCollision(entity, box)
    }

    /** Fliers may cling anywhere along a wall but want open air below so they perch above the
     * ground so we dont see them clipping, but walkers only cling where they can stand beside the wall.
     * Might need tweaked so that we add a check for an air block above too? we will see */
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

    /** We need to try to make sure the mon will fit on the wall it is attached to and that it is sturdy block */
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
            return pos.takeIf { isValidCeiling(world, pos, place) }
        }
        return null
    }

    /** A place that whitelists blocks may attach to not sturdy undersides like leaves;
     * without a whitelist only sturdy undersides count */
    private fun isValidCeiling(world: ServerLevel, pos: BlockPos, place: SleepPlace?): Boolean {
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
        private const val COLUMN_RADIUS_SQR = 0.25
        private const val DRIFT_RADIUS_SQR = 4.0
        private const val REACHABILITY_CHECKS = 4
        private const val SEARCH_INTERVAL = 20L
        private const val STALL_GIVE_UP_TICKS = 60
        private const val FAILED_SPOT_MEMORY_TICKS = 1200L
        private const val PLACE_SEARCH_FAILURE_TICKS = 100L
        private const val WALK_SPEED = 0.35F
    }
}