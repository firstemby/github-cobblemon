/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.spawner

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.BestSpawner
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.detail.SpawnPool
import com.cobblemon.mod.common.api.spawning.influence.SpawningInfluence
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.selection.SpawningSelector
import com.cobblemon.mod.common.util.weightedSelection

/**
 * Interface representing something that performs the action of spawning. Various functions
 * exist to streamline the process of using the [BestSpawner].
 *
 * @author Hiroku
 * @since January 24th, 2022
 */
interface Spawner {
    val name: String
    val influences: MutableList<SpawningInfluence>
    fun getSpawningSelector(): SpawningSelector<*>
    fun setSpawningSelector(selector: SpawningSelector<*>)
    fun getSpawnPool(): SpawnPool
    fun setSpawnPool(spawnPool: SpawnPool)
    fun <R> afterSpawn(action: SpawnAction<R>, result: R) {}

    fun getMatchingSpawns(bucket: SpawnBucket, spawnablePosition: SpawnablePosition): List<SpawnDetail> {
        val spawns = mutableListOf<SpawnDetail>()
        spawns.addAll(getSpawnPool().retrieve(bucket, spawnablePosition).filter { it.isSatisfiedBy(spawnablePosition) })
        spawnablePosition.influences.forEach { influence ->
            val influencedSpawns = influence.injectSpawns(bucket, spawnablePosition)
            if (influencedSpawns != null) {
                spawns.addAll(influencedSpawns)
            }
        }
        return spawns
    }

    fun copyInfluences() = influences.filter { !it.isExpired() }.toMutableList()

    fun chooseBucket(influences: List<SpawningInfluence>): SpawnBucket {
        val buckets = Cobblemon.bestSpawner.config.buckets
        val bucketWeights = buckets.associateWith { it.weight }.toMutableMap()
        influences.forEach { it.affectBucketWeights(bucketWeights) }
        return bucketWeights.entries.weightedSelection { it.value }?.key ?: buckets.first()
    }
}