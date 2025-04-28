/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.selection

import com.cobblemon.mod.common.Cobblemon.LOGGER
import com.cobblemon.mod.common.api.spawning.context.RegisteredSpawningContext
import com.cobblemon.mod.common.api.spawning.context.SpawningContext
import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.spawner.Spawner
import com.cobblemon.mod.common.util.weightedSelection
import kotlin.random.Random
import kotlin.reflect.full.createInstance

/**
 * A spawning selector that compiles a distinct list of all spawn details that
 * are possible across the context list, chooses which context type to spawn based
 * on a weighted selection of the number of spawns possible in it, performs a weighted
 * selection of spawns in that context type, and then chooses which of its contexts
 * to spawn them on based on their context-adjusted weights.
 *
 * The goal of this algorithm is to be kinder to spawns that are only possible
 * in very specific locational conditions by not letting the scarcity of suitable
 * locations hurt its chances of spawning. It also tries to allow areas with more or less
 * of an entire context type to favour the more populous.
 *
 * The weight for a spawn when doing primary selection is whichever context-influenced
 * weight is highest (as weight multipliers exist per spawning context) and then the
 * selection of which context to spawn the primary selected spawn uses the context
 * weight.
 *
 * At a glance:
 * - Spawn detail selection is flat across context quantity
 * - Spawn context type is chosen early by a selection prejudiced in favour of more common context types.
 *
 * @author Hiroku
 * @since July 10th, 2022
 */
open class FlatContextWeightedSelector : SpawningSelector {
    open fun getWeight(contextType: RegisteredSpawningContext<*>) = contextType.getWeight()

    protected class SelectingSpawnInformation {
        val spawningContexts = mutableMapOf<SpawningContext, Float>()
        var highestWeight = 0F

        fun add(spawnDetail: SpawnDetail, spawningContext: SpawningContext, contextTypeWeight: Float) {
            val weight = spawningContext.getWeight(spawnDetail) * contextTypeWeight
            spawningContexts[spawningContext] = weight
            if (weight > highestWeight) {
                highestWeight = weight
            }
        }

        fun chooseContext() = spawningContexts.keys.toList().weightedSelection { spawningContexts[it]!! }!!
    }

    protected class ContextSelectionData(
        val spawnsToContexts: MutableMap<SpawnDetail, SelectingSpawnInformation>,
        var percentSum: Float
    ) {
        val size: Int
            get() = spawnsToContexts.size
    }

    protected fun getSelectionData(
        spawner: Spawner,
        contexts: List<SpawningContext>
    ): Map<RegisteredSpawningContext<*>, ContextSelectionData> {
        val contextTypesToSpawns = mutableMapOf<RegisteredSpawningContext<*>, ContextSelectionData>()

        contexts.forEach { ctx ->
            val contextType = SpawningContext.getByClass(ctx)!!

            val possible = spawner.getMatchingSpawns(ctx)
            if (possible.isNotEmpty()) {
                val contextSelectionData = contextTypesToSpawns.getOrPut(contextType) { ContextSelectionData(mutableMapOf(), 0F) }
                possible.forEach {
                    // Only add to percentSum if this is the first time we've seen this SpawnDetail for this context
                    // type, otherwise the percentage will get amplified for every context the thing was possible,
                    // completely ruining the point of this pre-selection percentage.
                    if (it.percentage > 0 && !contextSelectionData.spawnsToContexts.containsKey(it)) {
                        contextSelectionData.percentSum += it.percentage
                    }

                    val selectingSpawnInformation = contextSelectionData.spawnsToContexts.getOrPut(
                        it,
                        SelectingSpawnInformation::class::createInstance
                    )
                    selectingSpawnInformation.add(it, ctx, getWeight(contextType))
                }
            }
        }

        return contextTypesToSpawns
    }

    override fun select(
        spawner: Spawner,
        contexts: List<SpawningContext>
    ): Pair<SpawningContext, SpawnDetail>? {
        val selectionData = getSelectionData(spawner, contexts)

        if (selectionData.isEmpty()) {
            return null
        }
 
        // Which context type should we use?
        val contextSelectionData = selectionData.entries.toList().weightedSelection { getWeight(it.key) * it.value.size }!!.value

        val spawnsToContexts = contextSelectionData.spawnsToContexts
        var percentSum = contextSelectionData.percentSum


        // First pass is doing percentage checks.
        if (percentSum > 0) {

            if (percentSum > 100) {
                LOGGER.warn(
                    """
                        A spawn list for ${spawner.name} exceeded 100% on percentage sums...
                        This means you don't understand how this option works.
                    """.trimIndent()
                )
                return null
            }

            /*
             * It's [0, 1) and I want (0, 1]
             * See half-open intervals here https://en.wikipedia.org/wiki/Interval_(mathematics)#Terminology
             */
            val selectedPercentage = 100 - Random.Default.nextFloat() * 100
            percentSum = 0F
            for ((spawnDetail, info) in spawnsToContexts) {
                if (spawnDetail.percentage > 0) {
                    percentSum += spawnDetail.percentage
                    if (percentSum >= selectedPercentage) {
                        return info.chooseContext() to spawnDetail
                    }
                }
            }
        }

        val selectedSpawn = spawnsToContexts.entries.toList().weightedSelection { it.value.highestWeight }

        // TODO clean this up later once we know the crash that happens is fixed
        if (selectedSpawn == null) {
            LOGGER.warn("FlatContextWeightedSelector failed: No spawn detail with positive weight was found for ${spawner.name}.")
            return null
        }

        return selectedSpawn.value.chooseContext() to selectedSpawn.key

    }

    protected fun getProbabilitiesFromContextType(spawner: Spawner, contextSelectionData: ContextSelectionData): Map<SpawnDetail, Float> {
        val percentSum = contextSelectionData.percentSum
        val weightPortion = 100 - percentSum
        val totalWeightMultiplier = 100 / weightPortion
        val spawnsToContexts = contextSelectionData.spawnsToContexts

        if (percentSum > 100) {
            LOGGER.warn(
                """
                    A spawn list for ${spawner.name} exceeded 100% on percentage sums...
                    This means you don't understand how this option works.
                """.trimIndent()
            )
            return emptyMap()
        }

        val totalWeights = mutableMapOf<SpawnDetail, Float>()
        var totalWeight = 0F

        for (spawn in spawnsToContexts.values) {
            totalWeight += spawn.highestWeight
        }

        val rescaledTotalWeight = totalWeight * totalWeightMultiplier
        val percentageWeight = (rescaledTotalWeight - totalWeight) / percentSum

        for ((spawnDetail, info) in spawnsToContexts.entries) {
            totalWeights[spawnDetail] = info.highestWeight + if (spawnDetail.percentage > 0) spawnDetail.percentage * percentageWeight else 0F
        }

        return totalWeights
    }

    override fun getTotalWeights(
        spawner: Spawner,
        contexts: List<SpawningContext>
    ): Map<SpawnDetail, Float> {
        val selectionData = getSelectionData(spawner, contexts)

        if (selectionData.isEmpty()) {
            return mapOf()
        }

        val totalWeights = mutableMapOf<SpawnDetail, Float>()

        val totalContextWeight = selectionData.keys.sumOf { getWeight(it).toDouble() }.toFloat()

        for ((contextType, contextSelectionData) in selectionData) {
            val contextWeightCorrection = getWeight(contextType) / totalContextWeight
            val contextProbabilities = getProbabilitiesFromContextType(spawner, contextSelectionData)

            contextProbabilities.entries.forEach {
                totalWeights[it.key] = it.value * contextWeightCorrection
            }
        }

        return totalWeights
    }
}