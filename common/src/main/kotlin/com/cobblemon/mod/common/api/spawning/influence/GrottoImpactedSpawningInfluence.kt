/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.spawning.influence

import com.cobblemon.mod.common.api.spawning.detail.SpawnDetail
import com.cobblemon.mod.common.api.spawning.position.SpawnablePosition
import net.minecraft.resources.ResourceLocation

/**
 * The grotto influence will normalize bucket weights and prevent non-habitat spawns from spawning.
 *
 * This influence exists once even if many grotto blocks are detected in the spawning zone.
 *
 * @author Hiroku
 * @since April 20th, 2025
 */
class GrottoImpactedSpawningInfluence(
    val structureIds: Set<ResourceLocation>
): BucketNormalizingInfluence(tier = 3) {
    companion object {
        const val HABITAT_LABEL = "habitat"
    }

    val markers = structureIds.map(ResourceLocation::toString)

    override fun affectSpawnablePosition(spawnablePosition: SpawnablePosition) {
        spawnablePosition.markers.addAll(markers)
    }

    override fun affectSpawnable(detail: SpawnDetail, spawnablePosition: SpawnablePosition) =  HABITAT_LABEL in detail.labels

    // TODO potentially insert spawns? Grotto block could supply some spawns

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