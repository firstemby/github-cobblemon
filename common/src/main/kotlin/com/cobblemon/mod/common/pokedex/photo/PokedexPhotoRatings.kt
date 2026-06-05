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
        val primary = photo.qualitySubjects.firstOrNull { it.primarySubject } ?: photo.qualitySubjects.firstOrNull()
        if (primary != null) {
            lines += Component.literal("Species: ${primary.species.substringAfter(':')}")
            lines += Component.literal("Size: +${primary.sizeScore}")
            lines += Component.literal("Centering: +${primary.placementScore}")

            val bonusLines = listOf(
                "Extra Pokemon" to primary.otherPokemonScore,
                "Looking at Camera" to primary.directionScore,
                "Herd" to primary.herdScore,
                "Wild" to primary.wildScore,
                "Form" to primary.formScore,
                "Pose" to primary.poseScore,
                "Habitat" to primary.habitatScore,
                "Time of Day" to primary.timeOfDayScore,
                "Weather" to primary.weatherScore,
                "Shiny" to primary.shinyScore,
                "Alpha" to primary.alphaScore
            ).filter { (_, score) -> score > 0 }
            if (bonusLines.isNotEmpty()) {
                lines += Component.literal("Bonus Points")
                bonusLines.forEach { (label, score) ->
                    lines += Component.literal("$label: +$score")
                }
            }

            val multipliers = listOf(
                "Rarity" to primary.rarityMultiplier,
                "Too Close" to primary.clippingMultiplier
            ).filter { (_, multiplier) -> multiplier != 1F }
            if (multipliers.isNotEmpty()) {
                lines += Component.literal("Multipliers:")
                multipliers.forEach { (label, multiplier) ->
                    lines += Component.literal("$label: x${formatMultiplier(multiplier)}")
                }
            }
        }

        lines += Component.literal("--------------------")
        lines += Component.literal("Total: ${photo.qualityScore}")
        lines += Component.literal("Rating: ${ratingText(photo.qualityScore)}")

        return lines
    }

    private fun formatMultiplier(value: Float): String {
        return String.format(Locale.ROOT, "%.2f", value)
    }

}
