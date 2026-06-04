/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.pokedex.photo

import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoMetadata
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRatings
import java.util.Locale

object PokedexPhotoSearch {
    enum class SortField {
        TIME_OF_DAY,
        BIOME,
        SPECIES,
        POINTS;

        fun next(): SortField {
            val values = entries
            return values[(ordinal + 1) % values.size]
        }
    }

    enum class SortDirection {
        ASCENDING,
        DESCENDING;

        fun toggled(): SortDirection = if (this == ASCENDING) DESCENDING else ASCENDING
    }

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

    fun filterAndSort(
        photos: List<PokedexPhotoMetadata>,
        rawQuery: String,
        sortField: SortField,
        sortDirection: SortDirection
    ): List<PokedexPhotoMetadata> {
        val filtered = filter(photos, rawQuery)
        val comparator = when (sortField) {
            SortField.TIME_OF_DAY -> compareBy<PokedexPhotoMetadata> { normalizedTime(it.timeOfDayTicks) }
                .thenByDescending { it.createdAt }
            SortField.BIOME -> compareBy<PokedexPhotoMetadata> { sortableResourceName(it.biome) }
                .thenByDescending { it.createdAt }
            SortField.SPECIES -> compareBy<PokedexPhotoMetadata> { primarySpeciesName(it) }
                .thenByDescending { it.createdAt }
            SortField.POINTS -> compareBy<PokedexPhotoMetadata> { PokedexPhotoRatings.starsForScore(it.qualityScore) }
                .thenBy { it.qualityScore }
                .thenByDescending { it.createdAt }
        }
        return if (sortDirection == SortDirection.ASCENDING) {
            filtered.sortedWith(comparator)
        } else {
            filtered.sortedWith(comparator.reversed())
        }
    }

    fun sortFieldLabel(sortField: SortField): String {
        return when (sortField) {
            SortField.TIME_OF_DAY -> "Time"
            SortField.BIOME -> "Biome"
            SortField.SPECIES -> "Species"
            SortField.POINTS -> "Stars"
        }
    }

    fun sortDirectionLabel(sortDirection: SortDirection): String {
        return when (sortDirection) {
            SortDirection.ASCENDING -> "Asc"
            SortDirection.DESCENDING -> "Desc"
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
        val time = normalizedTime(ticks)
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

    private fun normalizedTime(ticks: Long): Long {
        return ((ticks % DAY_LENGTH_TICKS) + DAY_LENGTH_TICKS) % DAY_LENGTH_TICKS
    }

    private fun sortableResourceName(value: String): String {
        return normalize(value.substringAfter(':', value))
    }

    private fun primarySpeciesName(photo: PokedexPhotoMetadata): String {
        val primary = photo.qualitySubjects.firstOrNull { it.primarySubject }?.species
            ?: photo.detectedSpecies.firstOrNull()
            ?: ""
        return sortableResourceName(primary)
    }

    private fun normalize(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace(nonAlphaNumeric, " ")
            .replace(repeatedWhitespace, " ")
            .trim()
    }
}
