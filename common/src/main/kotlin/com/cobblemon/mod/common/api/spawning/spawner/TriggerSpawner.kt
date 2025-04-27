/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.spawner

import com.cobblemon.mod.common.api.spawning.SpawnCause
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.selection.FlatSpawnablePositionWeightedSelector
import com.cobblemon.mod.common.api.spawning.selection.SpawningSelector
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel

/**
 * A type of spawner that occurs at a single point and provides its own means of generating a
 * spawnable position from a particular cause and position. The trigger spawner must be directly called to
 * spawn things (almost as if it was being triggered!!!) as opposed to being scheduled on a ticker.
 *
 * @author Hiroku
 * @since February 3rd, 2024
 */
abstract class TriggerSpawner<T : SpawnCause>(override val name: String, var spawns: SpawnPool) : Spawner {
    private var selector: SpawningSelector<*> = FlatSpawnablePositionWeightedSelector()
    override val influences = mutableListOf<SpawningInfluence>()

    open fun getSpawnAction(
        cause: T,
        world: ServerLevel,
        pos: BlockPos,
        influences: List<SpawningInfluence> = emptyList()
    ): SpawnAction<*>? {
        val spawnablePosition = parseSpawnablePosition(cause, world, pos) ?: return null
        spawnablePosition.influences.addAll(influences)
        val bucket = chooseBucket(cause, spawnablePosition.influences)
        return selector.select(this, bucket, listOf(spawnablePosition), max = 1).firstOrNull()
    }

    /** Parses a spawnable position, if possible, from the given cause and position. */
    abstract fun parseSpawnablePosition(cause: T, world: ServerLevel, pos: BlockPos): SpawnablePosition?

    override fun getSpawningSelector() = selector
    override fun setSpawningSelector(selector: SpawningSelector<*>) { this.selector = selector }
    override fun getSpawnPool() = spawns
    override fun setSpawnPool(spawnPool: SpawnPool) { spawns = spawnPool }
}