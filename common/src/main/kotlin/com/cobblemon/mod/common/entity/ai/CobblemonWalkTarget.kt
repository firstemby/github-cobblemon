/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.behavior.PositionTracker
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.pathfinder.PathType

/**
 * This is a trick to allow us to instruct the node-maker to only consider specific node types,
 * even if the entity can, in a pinch, use more. Can also have the entity sprint while it is further
 * than [sprintDistance] from the target, and count as arrived once within [stopDistance]. Both are measured
 * from the edge of its hitbox so big mons keep the same gap as small ones.
 *
 * @author Hiroku
 * @since March 24th, 2025
 */
class CobblemonWalkTarget(
    target: PositionTracker,
    speedModifier: Float,
    completionRange: Int,
    val nodeTypeFilter: (PathType) -> Boolean = { true },
    val destinationNodeTypeFilter: (PathType) -> Boolean = { true },
    val sprintDistance: Float? = null,
    val stopDistance: Float? = null
) : WalkTarget(
    target,
    speedModifier,
    completionRange
) {
    constructor(
        pos: BlockPos,
        speedModifier: Float,
        completionRange: Int,
        nodeTypeFilter: (PathType) -> Boolean = { true },
        destinationNodeTypeFilter: (PathType) -> Boolean = { true },
        sprintDistance: Float? = null,
        stopDistance: Float? = null
    ) : this(BlockPosTracker(pos), speedModifier, completionRange, nodeTypeFilter, destinationNodeTypeFilter, sprintDistance, stopDistance)
}