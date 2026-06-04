/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import java.util.Locale

object PokedexPhotoSearch {
    private const val DAY_LENGTH_TICKS = 24_000L
    private val nonAlphaNumeric = Regex("[^a-z0-9]+")
    private val repeatedWhitespace = Regex("\\s+")

    fun filter(photos: List<PokedexPhotoMetadata>, rawQuery: String): List<PokedexPhotoMetadata> {
        val tokens = parseQueryTokens(rawQuery)
        if (tokens.isEmpty()) {
            return photos
        }

        return photos.filter { photo ->
            val haystacks = buildHaystacks(photo)
            tokens.all { token -> haystacks.any { haystack -> haystack.contains(token) } }
        }
    }

    fun parseQueryTokens(rawQuery: String): List<String> {
        return rawQuery
            .split(',')
            .map(::normalize)
            .filter { it.isNotEmpty() }
    }

    private fun buildHaystacks(photo: PokedexPhotoMetadata): Set<String> {
        val haystacks = linkedSetOf<String>()

        haystacks += normalize(photo.id)
        haystacks += normalize(photo.index.toString())
        haystacks += normalize(photo.qualityScore.toString())
        haystacks += normalize("quality ${photo.qualityScore}")

        addResourceIdTerms(haystacks, photo.biome)
        addResourceIdTerms(haystacks, photo.dimension)
        addTimeTerms(haystacks, photo.timeOfDayTicks)

        photo.detectedSpecies.forEach { speciesId ->
            addResourceIdTerms(haystacks, speciesId)
        }

        return haystacks.filterTo(linkedSetOf()) { it.isNotEmpty() }
    }

    private fun addResourceIdTerms(haystacks: MutableSet<String>, value: String) {
        val lower = value.lowercase(Locale.ROOT)
        haystacks += normalize(lower)

        val path = lower.substringAfter(':', lower)
        haystacks += normalize(path)
        haystacks += normalize(path.replace('_', ' '))
        haystacks += normalize(path.replace('-', ' '))
        haystacks += normalize(path.replace('_', ' ').replace('-', ' '))
    }

    private fun addTimeTerms(haystacks: MutableSet<String>, ticks: Long) {
        val time = ((ticks % DAY_LENGTH_TICKS) + DAY_LENGTH_TICKS) % DAY_LENGTH_TICKS
        haystacks += normalize(time.toString())
        haystacks += normalize("time $time")
        haystacks += normalize("ticks $time")

        val labels = mutableListOf(
            if (time < 12_000L) "day" else "night"
        )

        when (time) {
            in 0L..999L, in 23_000L..23_999L -> labels += listOf("sunrise", "dawn", "morning")
            in 1_000L..5_999L -> labels += listOf("morning", "day")
            in 6_000L..8_999L -> labels += listOf("day", "noon")
            in 9_000L..11_999L -> labels += listOf("afternoon", "day")
            in 12_000L..12_999L -> labels += listOf("sunset", "dusk", "evening")
            in 13_000L..17_999L -> labels += listOf("night", "evening")
            else -> labels += listOf("night", "midnight")
        }

        labels.forEach { haystacks += normalize(it) }
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumeric, " ")
            .replace(repeatedWhitespace, " ")
            .trim()
    }
}
