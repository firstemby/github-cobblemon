/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.pokedex.photo

import net.minecraft.network.chat.Component
import java.util.Locale

object PokedexPhotoRatings {
    fun starsForScore(score: Int): Int {
        return when (score.coerceIn(0, 5000)) {
            in 0..1000 -> 1
            in 1001..2000 -> 2
            in 2001..3000 -> 3
            in 3001..4000 -> 4
            else -> 5
        }
    }

    fun starText(score: Int): String {
        val stars = starsForScore(score)
        return "★".repeat(stars) + "☆".repeat(5 - stars)
    }

    fun ratingText(score: Int): String {
        return "${starText(score)} (${starsForScore(score)}/5)"
    }

    fun scoreBreakdown(photo: PokedexPhotoMetadata): List<Component> {
        val lines = mutableListOf<Component>()
        lines += Component.literal("Rating: ${ratingText(photo.qualityScore)}")
        lines += Component.literal("Total: ${photo.qualityScore}/5000")
        if (photo.qualityBaseScore != photo.qualityScore || photo.qualitySpeciesMultiplier != 1F) {
            lines += Component.literal("Base before rarity: ${photo.qualityBaseScore}")
            lines += Component.literal("Species rarity: x${formatMultiplier(photo.qualitySpeciesMultiplier)}")
        }

        val primary = photo.qualitySubjects.firstOrNull { it.primarySubject } ?: photo.qualitySubjects.firstOrNull()
        if (primary != null) {
            lines += Component.literal("Primary: ${primary.species.substringAfter(':')}")
            lines += Component.literal("Size: +${primary.sizeScore} (${formatPercent(primary.rayCoverage)} coverage)")
            lines += Component.literal("Centering: +${primary.placementScore} (${formatPercent(1F - primary.centerDistance)} centered)")
            lines += Component.literal("Look at camera: +${primary.directionScore}")
            lines += Component.literal("Animation pose: +${primary.poseScore}")
            if (primary.wholeBodyInFrame) {
                lines += Component.literal("Full body: +${primary.fullBodyScore}")
            } else {
                lines += Component.literal("Out of frame: x${formatMultiplier(primary.clippingMultiplier)} (${primary.perimeterHits} perimeter hits)")
            }
            if (primary.otherPokemonScore > 0) {
                lines += Component.literal("Other Pokemon: +${primary.otherPokemonScore}")
            }
            if (primary.shiny) {
                lines += Component.literal("Shiny multiplier: x${formatMultiplier(primary.shinyMultiplier)}")
            }
            if (primary.activeAnimations.isNotEmpty()) {
                lines += Component.literal("Animations: ${primary.activeAnimations.joinToString(", ")}")
            }
            lines += Component.literal("Subject total: ${primary.subjectScore}")
        }

        val otherSubjects = photo.qualitySubjects.filterNot { it.primarySubject }.take(4)
        if (otherSubjects.isNotEmpty()) {
            lines += Component.literal("Other subjects:")
            otherSubjects.forEach { subject ->
                lines += Component.literal("  ${subject.species.substringAfter(':')}: +${subject.subjectScore}")
            }
        }

        return lines
    }

    private fun formatMultiplier(value: Float): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

    private fun formatPercent(value: Float): String {
        return String.format(Locale.ROOT, "%.1f%%", value.coerceIn(0F, 1F) * 100F)
    }
}
