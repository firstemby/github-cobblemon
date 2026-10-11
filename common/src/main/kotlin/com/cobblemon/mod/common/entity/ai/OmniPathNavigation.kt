/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.CobblemonBlocks
import com.cobblemon.mod.common.entity.OmniPathingEntity
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker
import com.cobblemon.mod.common.util.deleteNode
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import com.cobblemon.mod.common.util.toVec3d
import com.google.common.collect.ImmutableSet
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.tags.FluidTags
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.pathfinder.Node
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathComputationType
import net.minecraft.world.level.pathfinder.PathFinder
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator
import net.minecraft.world.phys.Vec3
import com.cobblemon.mod.common.util.math.arriveSpeed
import com.cobblemon.mod.common.util.math.slowDownSpeed
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A navigator designed to work with the [OmniPathNodeMaker], allowing a path that can cross land, water, and air
 * for entities that can do so. This is built on entities implementing the [OmniPathingEntity] interface. It doesn't
 * work fantastically.
 *
 * @author Hiroku
 * @since April 18th, 2025
 */
class OmniPathNavigation(val world: Level, val entity: Mob) : GroundPathNavigation(entity, world) {
    val pather = entity as OmniPathingEntity

    var cachedCurrentNode: Node? = null
    // Per node steering results for the path they were worked out for, by node index
    private var cachedSteeringPath: Path? = null
    private val tightCorners = mutableMapOf<Int, Boolean>()
    private val narrowNodes = mutableMapOf<Int, Boolean>()
    private val stepNodes = mutableMapOf<Int, Boolean>()
    private val steerPositions = mutableMapOf<Int, Vec3>()
    var currentNodeDistance = 0F

    data class NavigationContext(
        val onHit: (damage: DamageSource) -> Unit = {},
        val onRecalculate: (dueToDistance: Boolean) -> Unit = {},
        val onArrival: () -> Unit = {},
        val onCannotReach: () -> Unit = {},
        val sprinting: Boolean = false,
        val destinationProximity: Float = 0.01F,
        val destinationPathTypeFilter: (PathType) -> Boolean = { true },
    )

    var navigationContext = NavigationContext()
    // How far a steered mover travels while finishing a turn, given by its move control each tick
    private var turnDistance = 0.0
    // Whether whatever started the current path wants it sprinted. Cleared on every new path and on stop
    var sprinting = false

    /**
     * Where a steered mover should head this tick. [isFinal] means it should slow down and stop there.
     * [maxSpeed] is how fast it can go right now while still slowing in time for the corners ahead.
     */
    data class SteerTarget(
        val position: Vec3,
        val isFinal: Boolean,
        val maxSpeed: Double,
        val narrowAhead: Boolean = false,
        // Which way the path carries on from a node the target is waiting on, null when it isn't waiting on one
        val afterDirection: Vec3? = null
    )

    companion object {
        val verticallyPreciseNodeTypes = setOf(PathType.WATER, PathType.OPEN, PathType.LAVA)

        // Corners sharper than this start slowing the mob down
        const val CORNER_FREE_ANGLE_DEGREES = 45.0
        // Fraction of top speed kept going round a full reversal
        const val CORNER_MIN_SPEED_FRACTION = 0.5
        // Fraction of top speed kept going round a corner that can't be cut
        const val TIGHT_CORNER_SPEED_FRACTION = 0.3
        // Largest distance from a tight corner that still counts as reaching it, so big mobs can't turn early
        const val TIGHT_CORNER_REACH_DISTANCE = 0.5
    }

    override fun createPathFinder(range: Int): PathFinder {
        this.nodeEvaluator = OmniPathNodeMaker()
        nodeEvaluator.setCanOpenDoors(false)
        return PathFinder(nodeEvaluator, range)
    }

    override fun canUpdatePath(): Boolean {
        val (isInLiquid, isTouchingLava) = mob.level().getWaterAndLavaIn(mob.boundingBox)
        val isAtValidPosition = (!mob.isInLava && !mob.isEyeInFluid(FluidTags.LAVA)) ||
                (isTouchingLava && pather.canSwimInLava()) ||
                this.mob.isPassenger
        return if (pather.canSwimInWater()) {
            isInLiquid || ((this.mob.onGround() || pather.isFlying()) || this.mob.isInLiquid || this.mob.isPassenger)
        } else {
            isAtValidPosition
        }
    }

    override fun canFloat(): Boolean {
        return pather.canSwimInWater()
    }

    fun setCanPathThroughFire(canPathThroughFire: Boolean) {
        val omniPathNodeMaker = this.nodeEvaluator as OmniPathNodeMaker
        omniPathNodeMaker.canPathThroughFire = canPathThroughFire
    }

    // todo (techdaan): validate this is the right name
    override fun getTempMobPos() =
        Vec3(mob.x, getPathfindingY().toDouble(), mob.z)

    override fun followThePath() {
        val vec3d = this.tempMobPos

        val targetVec = targetPos?.toVec3d()?.add(0.5, 0.0, 0.5)
        if (targetVec != null && targetVec.distanceTo(vec3d) <= navigationContext.destinationProximity && path != null) {
            path = null
            cachedCurrentNode = null
            navigationContext.onArrival()
            // If we arrived at a not-flying destination
            val node = path?.nextNode?.type
            if (node != null && node != PathType.OPEN && pather.couldStopFlying()) {
                pather.setFlying(false)
            }
            return
        }

        maxDistanceToWaypoint = if (mob.bbWidth > 0.75f) mob.bbWidth / 2.0f else 0.75f - mob.bbWidth / 2.0f

        val currentNode = path!!.nextNode
        if (currentNode != cachedCurrentNode) {
            cachedCurrentNode = currentNode
            currentNodeDistance = currentNode.asVec3().distanceTo(entity.position()).toFloat()
        } else if (cachedCurrentNode != null && cachedCurrentNode!!.asVec3().distanceTo(entity.position()) > currentNodeDistance + 1) {
            recomputePath()
            navigationContext.onRecalculate(true)
            return
        }

        /*
         * The difference between this and the overrided function is that we use the vector
         * position for the d,e,f which improves behaviour of larger pokemon
         */
        val targetVec3d = path!!.getNextEntityPos(mob)
        val d = abs(mob.x - targetVec3d.x)
        val e = abs(mob.y - targetVec3d.y)
        val f = abs(mob.z - targetVec3d.z)
        val closeEnough = d < maxDistanceToWaypoint.toDouble()
                && f < this.maxDistanceToWaypoint.toDouble()
                && e < (if (currentNode.type in verticallyPreciseNodeTypes && (mob.isUnderWater || pather.isFlying())) maxDistanceToWaypoint else 1.0).toDouble()

        val reachedTightCorner = !mustTouch(path!!, path!!.nextNodeIndex) ||
            horizontalDistance(mob.position(), steerPosition(path!!, path!!.nextNodeIndex)) <= min(maxDistanceToWaypoint.toDouble(), TIGHT_CORNER_REACH_DISTANCE)

        // Corner cutting is commented out because it makes pokemon and NPCs 'cut' the corner and fall into water or lava
        if ((closeEnough && reachedTightCorner) || hasPassedNextNode(path!!)) {// || mob.navigation.canCutCorner(path!!.nextNode.type) && shouldTargetNextNodeInDirection(vec3d)) {
            path!!.advance()
            if (path!!.isDone) {
                path = null
                navigationContext.onArrival()
                // If we arrived at a not-flying destination
                if (currentNode.type != PathType.OPEN && pather.couldStopFlying()) {
                    pather.setFlying(false)
                }
            } else {
                val newNode = path!!.nextNode
                if (currentNode.type != newNode.type) {
                    if (newNode.type == PathType.OPEN) {
                        pather.setFlying(true)
                    } else if (currentNode.type != PathType.OPEN && pather.couldStopFlying()) { // if we just reached a non-flying node and the next node isn't a flying node, stop flying
                        pather.setFlying(false)
                    }
                }
            }
        }

        doStuckDetection(vec3d)
    }

    /**
     * Pure pursuit path following. The target is a point a lookahead distance further along the path than the
     * mob's closest point on it, so the mob eases back onto the path and rounds corners instead of zigzagging.
     * Also scans ahead for corners and the path end to work out how fast the mob can go and still slow in time.
     */
    fun getSteerTarget(maxSpeed: Double, speedBlendTicks: Double, turnDistance: Double): SteerTarget? {
        this.turnDistance = turnDistance
        val currentPath = path?.takeUnless { it.isDone } ?: return null
        val index = currentPath.nextNodeIndex
        val lastIndex = currentPath.nodeCount - 1
        if (lastIndex == 0) return SteerTarget(steerPosition(currentPath, 0), true, maxSpeed)
        // A fresh path starts next to the mob, so follow its first segment rather than seeking node 0 itself
        val segmentEnd = max(index, 1)

        val lookAhead = lookAheadDistance()
        // Far enough to see anything we'd need to start slowing for, stopping from top speed covers this much
        val scanDistance = max(lookAhead, maxSpeed * speedBlendTicks / 2)
        var point = closestPointOnSegment(mob.position(), steerPosition(currentPath, segmentEnd - 1), steerPosition(currentPath, segmentEnd))
        var distance = 0.0
        var carrot: Vec3? = null
        var speedLimit = maxSpeed
        var narrowAhead = isNarrow(currentPath, segmentEnd - 1)
        var afterDirection: Vec3? = null

        for (i in segmentEnd..lastIndex) {
            narrowAhead = narrowAhead || isNarrow(currentPath, i)
            val node = steerPosition(currentPath, i)
            val segmentLength = node.distanceTo(point)
            if (carrot == null && distance + segmentLength >= lookAhead) {
                carrot = point.add(node.subtract(point).normalize().scale(lookAhead - distance))
            }
            distance += segmentLength
            if (i == lastIndex) {
                // Brake to a stop on the end
                speedLimit = min(speedLimit, arriveSpeed(distance, maxSpeed, speedBlendTicks))
                break
            }
            if (distance > scanDistance) break
            val tight = mustTouch(currentPath, i)
            val cornerSpeed = maxSpeed * if (tight) TIGHT_CORNER_SPEED_FRACTION else cornerSpeedFraction(currentPath, i)
            speedLimit = min(speedLimit, slowDownSpeed(distance, cornerSpeed, speedBlendTicks))
            // Rounding this corner would leave the path, so the carrot waits on it until it's reached
            if (tight && carrot == null) {
                carrot = node
                afterDirection = steerPosition(currentPath, i + 1).subtract(node)
            }
            point = node
        }

        // Lookahead ran off the end so arrive on the last node
        return if (carrot == null) {
            SteerTarget(steerPosition(currentPath, lastIndex), true, speedLimit, narrowAhead)
        } else {
            SteerTarget(carrot, false, speedLimit, narrowAhead, afterDirection)
        }
    }

    /**
     * Aim at least as far ahead as the mob travels while finishing a turn otherwise it reaches the path before the
     * correction is done and crosses over it.
     */
    private fun lookAheadDistance() = max(turnDistance, mob.bbWidth.toDouble()) + mob.bbWidth / 2.0

    private fun closestPointOnSegment(pos: Vec3, start: Vec3, end: Vec3): Vec3 {
        val segment = end.subtract(start)
        val lengthSqr = segment.lengthSqr()
        if (lengthSqr == 0.0) return start
        val t = (pos.subtract(start).dot(segment) / lengthSqr).coerceIn(0.0, 1.0)
        return start.add(segment.scale(t))
    }

    /**
     * A walking corner where cutting the inside would cross a cell that isn't walkable, like a turn on a
     * narrow ledge or around a hole.
     */
    /** Nodes the mob has to properly reach rather than round off, tight corners and steps */
    private fun mustTouch(currentPath: Path, i: Int) = isTightCorner(currentPath, i) || isStep(currentPath, i)

    /**
     * A walking node next to a change in height
     */
    private fun isStep(currentPath: Path, i: Int): Boolean {
        refreshSteeringCache(currentPath)
        return stepNodes.getOrPut(i) {
            val node = currentPath.getNode(i)
            if (node.type != PathType.WALKABLE) return@getOrPut false
            val previous = if (i > 0) currentPath.getNode(i - 1) else null
            val next = if (i < currentPath.nodeCount - 1) currentPath.getNode(i + 1) else null
            (previous != null && previous.y != node.y) || (next != null && next.y != node.y)
        }
    }

    private fun isTightCorner(currentPath: Path, i: Int): Boolean {
        if (i <= 0 || i >= currentPath.nodeCount - 1) return false
        refreshSteeringCache(currentPath)
        return tightCorners.getOrPut(i) {
            val node = currentPath.getNode(i)
            if (node.type != PathType.WALKABLE) return@getOrPut false
            val position = steerPosition(currentPath, i)
            // The cell diagonally inside the turn. For a straight line this is the node's own cell
            val insidePosition = steerPosition(currentPath, i - 1).add(steerPosition(currentPath, i + 1)).subtract(position)
            val inside = BlockPos.containing(insidePosition.x, node.y.toDouble(), insidePosition.z)
            val cell = BlockPos.containing(position.x, node.y.toDouble(), position.z)
            inside != cell && WalkNodeEvaluator.getPathTypeStatic(mob, inside.mutable()) != PathType.WALKABLE
        }
    }

    /** A walking node with nothing walkable on either side of the path, like a 1 wide ledge */
    private fun isNarrow(currentPath: Path, i: Int): Boolean {
        refreshSteeringCache(currentPath)
        return narrowNodes.getOrPut(i) {
            val node = currentPath.getNode(i)
            if (node.type != PathType.WALKABLE || currentPath.nodeCount < 2) return@getOrPut false
            val position = steerPosition(currentPath, i)
            // Direction of the path through this node, from whichever neighbours it has
            val from = steerPosition(currentPath, max(i - 1, 0))
            val to = steerPosition(currentPath, min(i + 1, currentPath.nodeCount - 1))
            val along = Vec3(to.x - from.x, 0.0, to.z - from.z)
            if (along.lengthSqr() == 0.0) return@getOrPut false
            val side = Vec3(-along.z, 0.0, along.x).normalize()
            val left = BlockPos.containing(position.x + side.x, node.y.toDouble(), position.z + side.z)
            val right = BlockPos.containing(position.x - side.x, node.y.toDouble(), position.z - side.z)
            WalkNodeEvaluator.getPathTypeStatic(mob, left.mutable()) != PathType.WALKABLE &&
                WalkNodeEvaluator.getPathTypeStatic(mob, right.mutable()) != PathType.WALKABLE
        }
    }

    /**
     * Where the mob's centre should be at node [i]. Big mobs are checked over a footprint of blocks and a node counts
     * as walkable with only part of that footprint over ground, so they're centred over the walkable part instead.
     */
    private fun steerPosition(currentPath: Path, i: Int): Vec3 {
        refreshSteeringCache(currentPath)
        return steerPositions.getOrPut(i) {
            val default = currentPath.getEntityPosAtNode(mob, i)
            val node = currentPath.getNode(i)
            // Same footprint as OmniPathNodeMaker.getPathTypeOfMob
            val sizeX = (mob.boundingBox.xsize.toInt() + 1).coerceAtMost(2)
            val sizeZ = (mob.boundingBox.zsize.toInt() + 1).coerceAtMost(2)
            if (node.type != PathType.WALKABLE || (sizeX == 1 && sizeZ == 1)) return@getOrPut default

            val walkable = mutableListOf<BlockPos>()
            for (dx in 0 until sizeX) {
                for (dz in 0 until sizeZ) {
                    val pos = BlockPos(node.x + dx, node.y, node.z + dz)
                    if (WalkNodeEvaluator.getPathTypeStatic(mob, pos.mutable()) == PathType.WALKABLE) {
                        walkable.add(pos)
                    }
                }
            }
            if (walkable.isEmpty() || walkable.size == sizeX * sizeZ) {
                default
            } else {
                Vec3(walkable.map { it.x }.average() + 0.5, default.y, walkable.map { it.z }.average() + 0.5)
            }
        }
    }

    private fun refreshSteeringCache(currentPath: Path) {
        if (cachedSteeringPath !== currentPath) {
            cachedSteeringPath = currentPath
            tightCorners.clear()
            narrowNodes.clear()
            stepNodes.clear()
            steerPositions.clear()
        }
    }

    private fun horizontalDistance(a: Vec3, b: Vec3) = sqrt((a.x - b.x) * (a.x - b.x) + (a.z - b.z) * (a.z - b.z))

    /** 1 for gentle turns, falling to [CORNER_MIN_SPEED_FRACTION] for a full reversal at node [i]. */
    private fun cornerSpeedFraction(currentPath: Path, i: Int): Double {
        val node = steerPosition(currentPath, i)
        val incoming = node.subtract(steerPosition(currentPath, i - 1))
        val outgoing = steerPosition(currentPath, i + 1).subtract(node)
        if (incoming.lengthSqr() == 0.0 || outgoing.lengthSqr() == 0.0) return 1.0
        val cosAngle = incoming.normalize().dot(outgoing.normalize()).coerceIn(-1.0, 1.0)
        val angle = Math.toDegrees(acos(cosAngle))
        if (angle <= CORNER_FREE_ANGLE_DEGREES) return 1.0
        val sharpness = (angle - CORNER_FREE_ANGLE_DEGREES) / (180.0 - CORNER_FREE_ANGLE_DEGREES)
        return Mth.lerp(sharpness, 1.0, CORNER_MIN_SPEED_FRACTION)
    }

    /**
     * Steering rounds corners instead of touching each node, so a node counts as reached once the mob is past it
     * along its segment. Node type changes and the final node still need a real touch.
     */
    private fun hasPassedNextNode(currentPath: Path): Boolean {
        val index = currentPath.nextNodeIndex
        if (index >= currentPath.nodeCount - 1) return false
        if (currentPath.getNode(index).type != currentPath.getNode(index + 1).type) return false
        if (mustTouch(currentPath, index)) return false

        if (index == 0) {
            // Nothing behind the first node, so it's passed once the mob is on the far side of it
            val first = steerPosition(currentPath, 0)
            val outgoing = steerPosition(currentPath, 1).subtract(first)
            return mob.position().subtract(first).dot(outgoing) >= 0 && mob.position().distanceTo(first) < maxDistanceToWaypoint + lookAheadDistance()
        }

        val start = steerPosition(currentPath, index - 1)
        val end = steerPosition(currentPath, index)
        val along = mob.position().subtract(start).dot(end.subtract(start).normalize())
        return along >= end.distanceTo(start) && mob.position().distanceTo(end) < maxDistanceToWaypoint + lookAheadDistance()
    }

    fun isAirborne(world: Level, pos: BlockPos) =
        world.getBlockState(pos).isPathfindable(PathComputationType.AIR)
                && world.getBlockState(pos.below(1)).isPathfindable(PathComputationType.AIR)
                && world.getBlockState(pos.below(2)).isPathfindable(PathComputationType.AIR)

    fun findPath(target: BlockPos, distance: Int): Path? = createPath(ImmutableSet.of(target), 8, false, distance)

    override fun createPath(target: BlockPos, distance: Int): Path? {
        var target = target

        var blockPos: BlockPos
        if (this.world.getBlockState(target).isAir && !pather.canFly()) {
            blockPos = target.below()
            while (blockPos.y > this.world.minBuildHeight && this.world.getBlockState(blockPos).isAir) {
                blockPos = blockPos.below()
            }
            while (blockPos.y < this.world.maxBuildHeight && this.world.getBlockState(blockPos).isAir) {
                blockPos = blockPos.above()
            }
            target = blockPos
        }

        val blockState = this.world.getBlockState(target)
        val path = if (!blockState.isSolid
            || (blockState.`is`(BlockTags.LEAVES) && blockState.block == CobblemonBlocks.SACCHARINE_LEAVES && pather.canPathThroughSaccLeaves())) {
            findPath(target, distance)
        } else {
            blockPos = target.above()
            while (blockPos.y < this.world.maxBuildHeight && this.world.getBlockState(blockPos).isSolid) {
                blockPos = blockPos.above()
            }
            findPath(blockPos, distance)
        }

//        path?.let {
//            try {
//                var i = 0
//                while (true) {
//                    val node = it.getNode(i)
//                    val blockState = if (node.type == PathNodeType.OPEN) {
//                        Blocks.BONE_BLOCK.defaultState
//                    } else if (node.type == PathNodeType.WALKABLE) {
//                        Blocks.GOLD_BLOCK.defaultState
//                    } else if (node.type == PathNodeType.WATER) {
//                        Blocks.PACKED_ICE.defaultState
//                    } else {
//                        Blocks.COAL_BLOCK.defaultState
//                    }
//                    entity.level().setBlockState(node.blockPos, blockState)
//                    i++
//                }
//            } catch(e: Exception) {
//            }
//            entity.remove(Entity.RemovalReason.DISCARDED)
//        }

        return path
    }

    fun moveTo(x: Double, y: Double, z: Double, speed: Double = 1.0, navigationContext: NavigationContext) {
        this.navigationContext = navigationContext
        this.moveTo(x, y, z, speed)
        this.sprinting = navigationContext.sprinting
    }

    override fun isStableDestination(pos: BlockPos): Boolean {
        if (pather.canSwimInWater() && mob.isInWater) {
            val blockGetter: BlockGetter = level
            if (blockGetter.getFluidState(pos).`is`(FluidTags.WATER)) {
                return true
            }
        }
        if (mob.canBreatheUnderwater()) {
            val blockGetter: BlockGetter = level
            if (blockGetter.getFluidState(pos).`is`(FluidTags.WATER)) {
                val blockPos = pos.below()
                return level.getBlockState(blockPos).isSolidRender(this.level, blockPos)
            }
        }
        if (pather.canWalkOnWater()) {
            val blockGetter: BlockGetter = level
            if (blockGetter.getFluidState(pos.below()).`is`(FluidTags.WATER)) {
                return !level.getBlockState(pos).isSolidRender(this.level, pos)
            }
        }
        if (pather.canWalkOnLava()) {
            val blockGetter: BlockGetter = level
            if (blockGetter.getFluidState(pos.below()).`is`(FluidTags.LAVA)) {
                return !level.getBlockState(pos).isSolidRender(this.level, pos)
            }
        }
        if (pather.canFly()) {
            return this.level.getBlockState(pos).isAir || super.isStableDestination(pos)
            // Note the below is what is used by default for minecraft fliers
            // Mojang seems interested in anchoring flying mobs toward the ground
            // but we are decidedly not doing that.
            // this.level.getBlockState(pos).entityCanStandOn(this.level, pos, this.mob)
        }
        return super.isStableDestination(pos)
    }

    override fun getGroundY(vec: Vec3): Double {
        val blockGetter: BlockGetter = level
        val blockPos = BlockPos.containing(vec)
        if (pather.isFlying() && world.getBlockState(blockPos).isPathfindable(PathComputationType.AIR)) {
            // If we can fly and we're airborne, return the current Y position
            return vec.y
        }
        return if ((canFloat()) && blockGetter.getFluidState(blockPos).`is`(FluidTags.WATER)) vec.y + 0.5 else super.getGroundY(vec)
    }

    override fun createPath(entity: Entity, distance: Int): Path? {
        return this.createPath(entity.blockPosition(), distance)
    }

    override fun moveTo(path: Path?, speed: Double): Boolean {
        sprinting = false
        if (path != null && path.nodeCount > 0) {
            val node = path.getNode(0)!!
//            pokemonEntity.discard()
//            return false
            // If we just started moving and it's to an open node, fly
            if (node.type == PathType.OPEN && pather.canFly() && !pather.isFlying()) {
                pather.setFlying(true)
            }
        }

        return super.moveTo(path, speed)
    }

    override fun trimPath() {
        super.trimPath()
        val path = getPath() ?: return

        // What's there to trim
        if (path.nodeCount < 2) {
            return
        }

        /*
         * Sometimes entities spin in place at the start of their path
         * because of rounding stuff (I think) so try to detect those
         * cases and nix the first node so they just move forward.
         */
        val introNode = path.getNode(0)
        val subsequentNode = path.getNode(1)
        val mobMiddle = entity.position()
        val toIntroNode = introNode.asVec3().add(0.5, 0.0, 0.5).subtract(mobMiddle)
        val toSubsequentNode = subsequentNode.asVec3().add(0.5, 0.0, 0.5).subtract(mobMiddle)
        // if the first two nodes are pointing in opposite directions (>90 degrees) from the entity or the second node is closer, trim the first node
        if (toIntroNode.dot(toSubsequentNode) < 0 || toIntroNode.lengthSqr() > toSubsequentNode.lengthSqr()) {
            path.deleteNode(0)
        }


        var i = 2

        // Tries to skip some nodes that are all lined up
        skipLoop@
        while (i < path.nodeCount) {
            val firstNode = path.getNode(i - 2)
            val middleNode = path.getNode(i - 1)
            val nextNode = path.getNode(i)

            val nodeType = firstNode.type
            if (nodeType != middleNode.type || nodeType != nextNode.type || nodeType == PathType.WALKABLE) {
                i++
                continue
            }
            val directionToMiddle = middleNode.asBlockPos().subtract(firstNode.asBlockPos()).toVec3d().normalize()
            val directionToEnd = nextNode.asBlockPos().subtract(middleNode.asBlockPos()).toVec3d().normalize()

            // If we'd be making a big (greater than 45 degrees) turn by removing the middle node, that's a bit much, leave it alone.
            if (acos(directionToMiddle.dot(directionToEnd)) > PI / 4) {
                i++
                continue
            }

            var directionFromFirstToEnd = nextNode.asBlockPos().subtract(firstNode.asBlockPos()).toVec3d()
            val length = directionFromFirstToEnd.length()
            directionFromFirstToEnd = directionFromFirstToEnd.normalize()

            // Get all the nodes our hitbox would touch on our way there
            /*
            for (dist in 1..ceil(length).toInt() * 2) {
                val vec = firstNode.pos.add(directionFromFirstToEnd.multiply(dist.toDouble() / 2.0))
                val interveningNodeType = pokemonEntity.navigation.nodeMaker.getNodeType(, BlockPos(vec.x.toInt(), vec.y.toInt(), vec.z.toInt()))
                if (interveningNodeType != nodeType) {
                    i++
                    continue@skipLoop
                }
            }

             */

            // Construct a new node list that cuts out unnecessary in-between bits
            val remainingNodes = mutableListOf<Node>()
            var j = i
            while (j < path.nodeCount) {
                remainingNodes.add(path.getNode(j))
                j++
            }

            path.truncateNodes(i + remainingNodes.size - 1)
            for (k in remainingNodes.indices) {
                path.replaceNode(i - 1 + k, remainingNodes[k])
            }
        }

        // Could check for direct sunlight or rain or something
//        if (avoidSunlight) {
//            if (this.world.isSkyVisible(BlockPos(entity.x, entity.y + 0.5, entity.z))) {
//                return
//            }
//            for (i in 0 until currentPath!!.length) {
//                val pathNode = currentPath!!.getNode(i)
//                if (!this.world.isSkyVisible(BlockPos(pathNode.x, pathNode.y, pathNode.z))) continue
//                currentPath!!.length = i
//                return
//            }
//        }
    }

    fun getPathfindingY(): Int {
        val inSwimmableFluid = (mob.isEyeInFluid(FluidTags.WATER) && pather.canSwimInWater()) ||
                (mob.isEyeInFluid(FluidTags.LAVA) && pather.canSwimInLava())
        if (!inSwimmableFluid) {
            return Mth.floor(mob.y + 0.5)
        }

        return mob.blockY
    }

    override fun stop() {
        super.stop()
        sprinting = false
        this.currentNodeDistance = -1F
        this.cachedCurrentNode = null
        path = null
        nodeEvaluator.done()
        // In case a path is cancelled instead of completed, check if we should stop flying
        if (pather.couldStopFlying() && !isAirborne(entity.level(), entity.blockPosition())) {
            pather.setFlying(false)
        }
    }
}