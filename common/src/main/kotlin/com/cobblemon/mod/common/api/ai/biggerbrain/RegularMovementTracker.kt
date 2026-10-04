/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.ai.biggerbrain

import com.cobblemon.mod.common.api.ai.movement.TraversalPolicy
import com.cobblemon.mod.common.entity.ai.CobblemonWalkTarget
import com.cobblemon.mod.common.entity.ai.OmniPathNavigation
import com.cobblemon.mod.common.pokemon.ai.OmniPathNodeMaker
import com.cobblemon.mod.common.util.deleteNode
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.PathfinderMob
import net.minecraft.world.entity.ai.behavior.EntityTracker
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.entity.ai.util.DefaultRandomPos
import net.minecraft.world.level.pathfinder.Path
import net.minecraft.world.level.pathfinder.PathType
import net.minecraft.world.phys.Vec3

/**
 * The default movement tracker used for tracking movement towards a [WalkTarget]. This is pretty much entirely
 * just the code for FollowWalkTargetTask from Minecraft brain stuff.
 *
 * @author Hiroku
 * @since December 29th, 2025
 */
class RegularMovementTracker(
    val entity: PathfinderMob,
    override val walkTarget: WalkTarget,
    /**
     * The traversal rules for this movement. They stay attached to the path, including when it's recomputed or
     * re-planned because the target moved. Null uses the legacy rules. Only honoured by [OmniPathNavigation].
     */
    policy: TraversalPolicy? = null,
    /** An already planned path to follow instead of planning one here. */
    initialPath: Path? = null
) : MovementTracker {
    companion object {
        /**
         * Attempts to create a tracker for the given entity and target. Returns null if the path
         * couldn't be created.
         */
        fun attempt(entity: PathfinderMob, walkTarget: WalkTarget, policy: TraversalPolicy? = null): RegularMovementTracker? {
            return RegularMovementTracker(entity, walkTarget, policy).takeIf { it.isValid }
        }

        /** Creates a tracker that follows a path which was already planned, under the policy it was planned with. */
        fun following(entity: PathfinderMob, walkTarget: WalkTarget, path: Path, policy: TraversalPolicy?): RegularMovementTracker? {
            return RegularMovementTracker(entity, walkTarget, policy, path).takeIf { it.isValid }
        }
    }

    /** The policy given explicitly, otherwise whatever the walk target asks for. */
    val policy: TraversalPolicy? = policy ?: (walkTarget as? CobblemonWalkTarget)?.policy

    var isValid = false
        private set
    var started = false

    override var lastStatus: MovementStatus = MovementStatus.PENDING
    private var path: Path? = null
    private var lastKnownTargetPos: BlockPos? = null

    init {
        val hasReached = hasReached(entity, walkTarget)
        if (!hasReached && initialPath != null) {
            this.path = initialPath
            this.lastKnownTargetPos = walkTarget.target.currentBlockPosition()
            isValid = true
        } else if (!hasReached && hasFinishedPath(entity, walkTarget)) {
            this.lastKnownTargetPos = walkTarget.target.currentBlockPosition()
            isValid = true
        } else {
            lastStatus = MovementStatus.FAILED
        }
    }

    override fun tick(): MovementStatus {
        val path = path
        val status = if (path == null || isTargetSpectator(walkTarget)) {
            stop(entity)
            MovementStatus.FAILED
        } else if (hasReached(entity, walkTarget)) {
            stop(entity)
            MovementStatus.COMPLETED
        } else if (started && entity.navigation.isDone) {
            stop(entity)
            MovementStatus.FAILED
        } else {
            if (!started) {
                startOnPath(entity)
                started = true
            }

            tick(entity)
            MovementStatus.PENDING
        }
        this.lastStatus = status
        return status
    }

    fun startOnPath(entity: PathfinderMob) {
        entity.brain.setMemory(MemoryModuleType.PATH, this.path)
        entity.navigation.moveTo(this.path, walkTarget.speedModifier.toDouble())
    }

    override fun stop() {
        stop(entity)
    }

    fun stop(entity: PathfinderMob) {
        entity.navigation.stop()
        entity.brain.eraseMemory(MemoryModuleType.WALK_TARGET)
        entity.brain.eraseMemory(MemoryModuleType.PATH)
    }

    private fun tick(entity: PathfinderMob) {
        val path = entity.navigation.path
        if (this.path !== path) {
            this.path = path
            this.startOnPath(entity)
        }

        if (path != null && lastKnownTargetPos != null) {
            if (walkTarget.target.currentBlockPosition().distSqr(lastKnownTargetPos!!) > 4.0) {
                // The target has moved away from where we are pathing, try pathing to its new location
                if (hasFinishedPath(entity, walkTarget)) {
                    lastKnownTargetPos = walkTarget.target.currentBlockPosition()
                    startOnPath(entity)
                } else {
                    // Uh oh, we can't get closer? Maybe next tick will be better?
                }
            }
        }
    }

    private fun withNodeFilter(
        entity: PathfinderMob,
        nodeFilter: Predicate<PathType>,
        block: () -> Unit) {
        val nodeEvaluator = entity.navigation.nodeEvaluator
        if (nodeEvaluator is OmniPathNodeMaker) {
            nodeEvaluator.nodeFilter = nodeFilter
            block()
            nodeEvaluator.nodeFilter = Predicate { true }
        } else {
            block()
        }
    }

    private fun hasFinishedPath(entity: PathfinderMob, walkTarget: WalkTarget): Boolean {
        val blockPos = walkTarget.target.currentBlockPosition()
        this.path = generatePath(entity, walkTarget, blockPos)
        val hasReached = hasReached(entity, walkTarget)
        if (!hasReached && path == null) {
            // Desperate attempt to still path to the destination by going somewhere in the vicinity
            val vec3d = DefaultRandomPos.getPosTowards(
                entity,
                10,
                7,
                Vec3.atBottomCenterOf(blockPos),
                Math.PI / 2F
            )
            if (vec3d != null) {
                this.path = generatePath(entity, walkTarget, BlockPos.containing(vec3d))
                return this.path != null
            }
        } else if (!hasReached) {
            return true
        }

        return false
    }

    private fun generatePath(
        entity: PathfinderMob,
        walkTarget: WalkTarget,
        target: BlockPos
    ): Path? {
        var path: Path? = null
        val navigation = entity.navigation
        if (policy != null && navigation is OmniPathNavigation) {
            // The policy replaces node filtering; destination filtering still applies below.
            var planned = navigation.planPath(target, 0, policy)
            if (planned != null && walkTarget is CobblemonWalkTarget) {
                while (planned.endNode?.type?.let { walkTarget.destinationNodeTypeFilter(it) } == false) {
                    planned.deleteNode(planned.nodeCount - 1)
                }
                if (planned.nodeCount == 0) {
                    planned = null
                }
            }
            path = planned
        } else if (walkTarget is CobblemonWalkTarget) {
            withNodeFilter(
                entity = entity,
                nodeFilter = walkTarget.nodeTypeFilter,
            ) {
                var resolvedPath = entity.navigation.createPath(target, 0)
                if (resolvedPath != null) {
                    // A CobblemonWalkTarget may have a destinationNodeTypeFilter, which is used to filter the end node of the path.
                    // Mainly useful for when the thing setting the target did it a bit crudely or didn't know how far the navigator
                    // was going to get, and might land on something that is technically traversable by the entity but it doesn't
                    // fit the intention of the task. Example, a little bird wandering might end up finishing on a flight node,
                    // but it doesn't really want to fly in place so it'd prefer not ending there.
                    while (resolvedPath.endNode?.type?.let { walkTarget.destinationNodeTypeFilter(it) } == false) {
                        resolvedPath.deleteNode(resolvedPath.nodeCount - 1)
                    }
                    if (resolvedPath.nodeCount == 0) {
                        resolvedPath = null
                    }
                }
                path = resolvedPath
            }
        } else {
            path = entity.navigation.createPath(target, 0)
        }
        return path
    }

    private fun hasReached(entity: PathfinderMob, walkTarget: WalkTarget): Boolean {
        return walkTarget.target.currentBlockPosition().distManhattan(entity.blockPosition()) <= walkTarget.closeEnoughDist
    }

    fun isTargetSpectator(target: WalkTarget): Boolean {
        val lookTarget = target.target
        return if (lookTarget is EntityTracker) {
            lookTarget.entity.isSpectator
        } else {
            false
        }
    }
}