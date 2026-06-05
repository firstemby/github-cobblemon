/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import java.util.UUID

data class PokedexPhotoMetadata(
    val id: String,
    val owner: UUID,
    val index: Long,
    val width: Int,
    val height: Int,
    val createdAt: Long,
    val dataSize: Int,
    val detectedSpecies: List<String> = emptyList(),
    val biome: String = "minecraft:unknown",
    val dimension: String = "minecraft:overworld",
    val timeOfDayTicks: Long = 0L,
    val qualityScore: Int = 0,
    val qualityBaseScore: Int = 0,
    val qualitySpeciesMultiplier: Float = 1F,
    val qualitySubjects: List<PokedexPhotoSubjectQuality> = emptyList()
)

data class PokedexPhotoSubjectQuality(
    val species: String,
    @Transient val entityUuid: UUID? = null,
    val primarySubject: Boolean = false,
    val subjectScore: Int = 0,
    val sizeScore: Int = 0,
    val placementScore: Int = 0,
    val centerDistance: Float = 1F,
    val distanceScore: Int = 0,
    val directionScore: Int = 0,
    val poseScore: Int = 0,
    val fullBodyScore: Int = 0,
    val otherPokemonScore: Int = 0,
    val rayHits: Int = 0,
    val rayCoverage: Float = 0F,
    val perimeterHits: Int = 0,
    val wholeBodyInFrame: Boolean = true,
    val clippingMultiplier: Float = 1F,
    val activeAnimations: List<String> = emptyList(),
    val shiny: Boolean = false,
    val shinyMultiplier: Float = 1F,
    val rarityMultiplier: Float = 1F
)

