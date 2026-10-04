/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.entity.ai

import com.cobblemon.mod.common.api.ai.movement.TraversalPolicy
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.ai.behavior.BlockPosTracker
import net.minecraft.world.entity.ai.memory.WalkTarget
import net.minecraft.world.level.pathfinder.PathType

/**
 * This is a trick to allow us to instruct the node-maker to only consider specific node types,
 * even if the entity can, in a pinch, use more.
 *
 * @author Hiroku
 * @since March 24th, 2025
 */
class CobblemonWalkTarget(
    pos: BlockPos,
    speedModifier: Float,
    completionRange: Int,
    val nodeTypeFilter: (PathType) -> Boolean = { true },
    val destinationNodeTypeFilter: (PathType) -> Boolean = { true },
    /**
     * The traversal rules for reaching this target. Where supported (the bigger brain's movement trackers) this is
     * used instead of [nodeTypeFilter].
     */
    val policy: TraversalPolicy? = null
) : WalkTarget(
    BlockPosTracker(pos),
    speedModifier,
    completionRange
)