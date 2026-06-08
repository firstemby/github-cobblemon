/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.selection

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.SpawnBucket
import com.cobblemon.mod.common.api.spawning.detail.SpawnAction
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.util.removeIf
import com.cobblemon.mod.common.util.weightedSelection
import kotlin.random.Random

/**
 * A spawning selector that compiles a distinct list of all spawn details that
 * are possible across any spawnable position type and performs a weighted selection of spawns.
 *
 * The goal of this algorithm is to be kinder to spawns that are only possible
 * in very specific locational conditions or spawnable position types by not letting the scarcity
 * of suitable locations hurt its chances of spawning. It differs from the
 * [FlatSpawnablePositionWeightedSelector] because here a spawnable position type only appearing once
 * will not make it any less likely to be selected.
 *
 * A spawn detail's weight when selecting between all possible spawn details will be the
 * highest of its spawn position-adjusted weights. In other words, if a spawn is possible at both A and
 * B spawnable positions but is weighed more highly at A due to a weight multiplier, it will use the
 * weight of the spawn at A when choosing which spawn should be picked.
 *
 * When choosing which spawnable position a specific spawn detail will spawn at once that spawn detail
 * has been chosen, it does a spawnable position-weighted random selection across those spawnable positions.
 *
 * At a glance:
 * - Spawn detail selection is flat across spawnable position quantity
 * - The popularity of specific spawnable position types has no bearing on the spawn chance.
 *
 * @author Hiroku
 * @since July 10th, 2022
 */
open class FlatSelector : SpawningSelector<FlatSelector.SpawnablePositionSelectionData> {
    class SelectingSpawnInformation(val spawnDetail: SpawnDetail) {
        val spawnablePositions = mutableMapOf<SpawnablePosition, Float>()
        var highestWeight = 0F

        fun add(spawnablePosition: SpawnablePosition, spawnablePositionWeight: Float) {
            spawnablePositions[spawnablePosition] = spawnablePositionWeight
            if (spawnablePositionWeight > highestWeight) {
                highestWeight = spawnablePositionWeight
            }
        }

        fun chooseSpawnablePosition() = spawnablePositions.entries.weightedSelection { it.value }!!.key
    }

    class SpawnablePositionBucketSelectionData(
        val selectingSpawnInformation: MutableList<SelectingSpawnInformation>
    ) {
        var percentSum: Float = 0F
        val seenSpawns = mutableListOf<SpawnDetail>()

        fun add(spawnDetail: SpawnDetail, spawnablePosition: SpawnablePosition) {
            // Only add to percentSum if this is the first time we've seen this SpawnDetail, otherwise
            // the percentage will get amplified for every spawnable position the thing was possible, completely
            // ruining the point of this pre-selection percentage.
            if (spawnDetail.percentage > 0 && spawnDetail !in seenSpawns) {
                seenSpawns.add(spawnDetail)
                percentSum += spawnDetail.percentage
            }
            val spawnInformation = selectingSpawnInformation.find { it.spawnDetail == spawnDetail }
                ?: SelectingSpawnInformation(spawnDetail).also { selectingSpawnInformation.add(it) }
            spawnInformation.add(spawnablePosition, spawnablePosition.getWeight(spawnDetail))
        }

        fun remove(spawnInformation: SelectingSpawnInformation) {
            selectingSpawnInformation -= spawnInformation
            if (spawnInformation.spawnDetail.percentage > 0) {
                percentSum -= spawnInformation.spawnDetail.percentage
            }
        }
    }

    class SpawnablePositionSelectionData(
        val spawner: Spawner,
        val spawnablePositions: List<SpawnablePosition>,
        val selectingSpawnInformation: MutableMap<SpawnBucket, SpawnablePositionBucketSelectionData>,
    ): SpawnSelectionData {
        override val spawnActions = mutableListOf<SpawnAction<*>>()
        override val context = mutableMapOf<String, Any>()

        fun getDataForBucket(bucket: SpawnBucket): SpawnablePositionBucketSelectionData {
            val existing = selectingSpawnInformation[bucket]
            if (existing != null) {
                return existing
            }

            val bucketSpawnInformation = SpawnablePositionBucketSelectionData(mutableListOf())
            spawnablePositions.forEach { spawnablePosition ->
                spawner.getMatchingSpawns(bucket, spawnablePosition).forEach {
                    bucketSpawnInformation.add(it, spawnablePosition)
                }
            }

            selectingSpawnInformation[bucket] = bucketSpawnInformation
            return bucketSpawnInformation
        }

        override fun removeSpawnDetails(shouldRemove: (SpawnDetail) -> Boolean) {
            selectingSpawnInformation.flatMap { it.value.selectingSpawnInformation.filter { shouldRemove(it.spawnDetail) } }
                .forEach { selectingSpawnInformation[it.spawnDetail.bucket]?.remove(it) }
        }

        override fun removeSpawnablePositions(shouldRemove: (SpawnDetail, SpawnablePosition) -> Boolean) {
            val toRemove = selectingSpawnInformation.flatMap { it.value.selectingSpawnInformation }.filter { positionData ->
                positionData.spawnablePositions.removeIf { shouldRemove(positionData.spawnDetail, it.key) }
                positionData.highestWeight = positionData.spawnablePositions.maxOfOrNull { it.value } ?: 0F
                positionData.spawnablePositions.isEmpty()
            }

            toRemove.forEach { selectingSpawnInformation[it.spawnDetail.bucket]?.remove(it) }
        }
    }

    override fun getSelectionData(
        spawner: Spawner,
        spawnablePositions: List<SpawnablePosition>
    ): SpawnablePositionSelectionData {
        val selectingSpawnInformation: MutableMap<SpawnBucket, SpawnablePositionBucketSelectionData> = mutableMapOf()

        return SpawnablePositionSelectionData(spawner, spawnablePositions, selectingSpawnInformation)
    }

    override fun selectSpawnAction(
        spawner: Spawner,
        bucket: SpawnBucket,
        selectionData: SpawnablePositionSelectionData,
    ): SpawnAction<*>? {
        val selectingSpawnInformation = selectionData.getDataForBucket(bucket) ?: return null
        var percentSum = selectingSpawnInformation.percentSum

        // First pass is doing percentage checks.
        if (percentSum > 0) {

            if (percentSum > 100) {
                Cobblemon.LOGGER.warn(
                    """
                        A spawn list for ${spawner.name} exceeded 100% on percentage sums in bucket ${bucket.name}...
                        This means you don't understand how this option works.
                    """.trimIndent()
                )
                return null
            }

            /*
             * It's [0, 1) and I want (0, 1]
             * See half-open intervals here https://en.wikipedia.org/wiki/Interval_(mathematics)#Terminology
             */
            val selectedPercentage = 100 - Random.nextFloat() * 100
            percentSum = 0F
            for (info in selectingSpawnInformation.selectingSpawnInformation) {
                if (info.spawnDetail.percentage > 0) {
                    percentSum += info.spawnDetail.percentage
                    if (percentSum >= selectedPercentage) {
                        return info.spawnDetail.choose(
                            spawnablePosition = info.chooseSpawnablePosition(),
                            bucket = bucket,
                            selectionData = selectionData
                        )
                    }
                }
            }
        }

        val selectedSpawn = selectingSpawnInformation.selectingSpawnInformation.toList().weightedSelection { it.highestWeight }!!
        return selectedSpawn.spawnDetail.choose(
            spawnablePosition = selectedSpawn.chooseSpawnablePosition(),
            bucket = bucket,
            selectionData = selectionData
        )
    }

    override fun getTotalWeights(
        spawner: Spawner,
        bucket: SpawnBucket,
        spawnablePositions: List<SpawnablePosition>
    ): Map<SpawnDetail, Float> {
        val selectionData = getSelectionData(spawner, spawnablePositions).getDataForBucket(bucket)

        if (selectionData.selectingSpawnInformation.isEmpty()) {
            return emptyMap()
        }

        val totalWeights = mutableMapOf<SpawnDetail, Float>()

        for (info in selectionData.selectingSpawnInformation) {
            totalWeights[info.spawnDetail] = info.highestWeight
        }

        return totalWeights
    }
}