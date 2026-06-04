/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.spawning.CobblemonSpawnPools
import com.cobblemon.mod.common.api.spawning.detail.PokemonSpawnDetail
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.resources.ResourceLocation
import kotlin.math.roundToInt

object PokedexPhotoQuality {
    data class AppliedScore(
        val score: Int,
        val baseScore: Int,
        val speciesMultiplier: Float
    )

    fun applySpeciesMultiplier(baseScore: Int, detectedSpecies: List<String>): AppliedScore {
        val safeBaseScore = baseScore.coerceIn(0, 5000)
        val multiplier = detectedSpecies
            .asSequence()
            .mapNotNull(ResourceLocation::tryParse)
            .map(::speciesRarityMultiplier)
            .maxOrNull()
            ?: 1F

        return AppliedScore(
            score = (safeBaseScore * multiplier).roundToInt().coerceIn(0, 5000),
            baseScore = safeBaseScore,
            speciesMultiplier = multiplier
        )
    }

    fun speciesRarityMultiplier(species: ResourceLocation): Float {
        val bucketRank = runCatching {
            CobblemonSpawnPools.WORLD_SPAWN_POOL
                .asSequence()
                .filterIsInstance<PokemonSpawnDetail>()
                .filter { detail -> detail.pokemon.species?.asIdentifierDefaultingNamespace() == species }
                .map { detail -> rankBucket(detail.bucket.name) }
                .maxOrNull()
        }.getOrNull() ?: 0

        return when (bucketRank) {
            4 -> Cobblemon.config.pokedexScannerPhotoUltraRareSpeciesMultiplier.coerceAtLeast(1F)
            3 -> Cobblemon.config.pokedexScannerPhotoRareSpeciesMultiplier.coerceAtLeast(1F)
            2 -> Cobblemon.config.pokedexScannerPhotoUncommonSpeciesMultiplier.coerceAtLeast(1F)
            else -> 1F
        }
    }

    private fun rankBucket(bucketName: String): Int {
        return when (bucketName.lowercase()) {
            "ultra-rare", "ultrarare", "ultra_rare" -> 4
            "rare" -> 3
            "uncommon" -> 2
            "common" -> 1
            else -> 0
        }
    }
}
