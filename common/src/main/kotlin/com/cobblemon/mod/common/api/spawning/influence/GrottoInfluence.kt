/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.block.entity.GrottoBlockEntity

/**
 * The grotto influence will force spawns to all be for the most common bucket.
 */
class GrottoInfluence(
    private val grottoBlockEntity: GrottoBlockEntity
) : SpawningInfluence {
    companion object {
        const val HABITAT_LABEL = "habitat"
    }

    override fun affectSpawnable(detail: SpawnDetail, spawnablePosition: SpawnablePosition) =  HABITAT_LABEL in detail.labels
    override fun affectBucketWeight(bucket: SpawnBucket, weight: Float, allBuckets: List<SpawnBucket>): Float {
        val rarestBuckets = allBuckets.sortedByDescending { it.weight }
        val numberOfBuckets = rarestBuckets.size
        var subtractedAmount = 0F
        val index = rarestBuckets.indexOf(bucket)

        for (i in 0 until (numberOfBuckets - 1)) {
            val it = rarestBuckets[i]

        }
//        for (rareBucket in rarestBuckets) {
//            subtractedAmount += rarestBuckets[i].weight
//        }

        if (bucket == allBuckets.maxByOrNull { it.weight }) {
            // If this is the highest weight bucket, we want to boost it
            return 1F
        } else {
            return 0F
        }
    }

    /*companion object {
        private val LOGGER = LoggerFactory.getLogger("GrottoInfluence")
    }

    override fun affectSpawnable(detail: SpawnDetail, spawnablePosition: SpawningContext): Boolean {
        if (detail !is PokemonSpawnDetail) return true
        val detailId = ResourceLocation.tryParse(detail.pokemon.species!!) ?: return false

        // If strict filtering enabled, reject anything not in allowed list
        if (allowedSpawns != null && !allowedSpawns.contains(detailId)) {
            LOGGER.info("[GrottoInfluence] Rejecting ${detailId.path} — not in current moon phase group.")
            return false
        }

        return true
    }

    override fun affectWeight(detail: SpawnDetail, spawnablePosition: SpawningContext, weight: Float): Float {
        if (detail !is PokemonSpawnDetail) return weight
        val detailId = ResourceLocation.tryParse(detail.pokemon.species!!) ?: return weight

        val boostTargetList = allowedSpawns ?: Habitats.getHabitat(structureId)?.spawnPool?.flatten() ?: emptyList()
        return if (boostTargetList.contains(detailId)) {
            LOGGER.info("[GrottoInfluence] Boosting spawn weight for ${detailId.path} (structure: $structureId)")
            weight * 100f
        } else {
            weight
        }
    }*/
}