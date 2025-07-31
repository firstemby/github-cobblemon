/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.entity.OmniPathingEntity
import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker
import com.cobblemon.mod.common.util.getWaterAndLavaIn
import com.cobblemon.mod.common.util.toVec3d
import com.google.common.collect.ImmutableSet
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import net.minecraft.core.BlockPos
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

    companion object {
        val verticallyPreciseNodeTypes = setOf(PathType.WATER, PathType.OPEN, PathType.LAVA)
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
                && e < (if (currentNode.type in verticallyPreciseNodeTypes) maxDistanceToWaypoint else 1.0).toDouble()

        // Corner cutting is commented out because it makes pokemon and NPCs 'cut' the corner and fall into water or lava
        if (closeEnough) {// || mob.navigation.canCutCorner(path!!.nextNode.type) && shouldTargetNextNodeInDirection(vec3d)) {
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

        val path = if (!this.world.getBlockState(target).isSolid) {
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
    }

    override fun getGroundY(vec: Vec3): Double {
        val blockGetter: BlockGetter = level
        val blockPos = BlockPos.containing(vec)
        if (pather.isFlying() && world.getBlockState(blockPos).isPathfindable(PathComputationType.AIR)) {
            // If we can fly and we're airborne, return the current Y position
            return vec.y
        }
        return if ((canFloat()) && blockGetter.getFluidState(blockPos).`is`(FluidTags.WATER)) vec.y + 0.5 else WalkNodeEvaluator.getFloorLevel(blockGetter, blockPos)
    }

    override fun createPath(entity: Entity, distance: Int): Path? {
        return this.createPath(entity.blockPosition(), distance)
    }

    override fun moveTo(path: Path?, speed: Double): Boolean {
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
        var i = 2

        // Tries to skip some nodes that are all lined up
        skipLoop@
        while (i < path.nodeCount) {
            val firstNode = path.getNode(i - 2)
            val middleNode = path.getNode(i - 1)
            val nextNode = path.getNode(i)

            val directionToMiddle = middleNode.asBlockPos().subtract(firstNode.asBlockPos()).toVec3d().normalize()
            val nodeType = firstNode.type
            if (nodeType != middleNode.type || nodeType != nextNode.type || nodeType == PathType.WALKABLE) {
                i++
                continue
            }

            val directionToEnd = nextNode.asBlockPos().subtract(middleNode.asBlockPos()).toVec3d().normalize()

            // If we'd be making a big (greater than 45 degrees) turn by removing the middle node, that's a bit much, leave it alone.
            if (acos(directionToMiddle.dot(directionToEnd)) > PI / 3) {
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