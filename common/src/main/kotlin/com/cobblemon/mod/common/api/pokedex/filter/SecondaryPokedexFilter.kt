/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.filter

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.resources.ResourceLocation

enum class SecondaryPokedexFilterType {
    ALL,
    CAUGHT,
    SEEN,
    UNDISCOVERED,
    LEVEL_UP_TM_UNDISCOVERED
}

class SecondaryPokedexFilter(
    private val pokedexManager: AbstractPokedexManager,
    private val filterType: SecondaryPokedexFilterType
) : EntryFilter() {

    override fun test(entry: PokedexEntry): Boolean {
        return when (filterType) {
            SecondaryPokedexFilterType.ALL -> true
            SecondaryPokedexFilterType.CAUGHT -> pokedexManager.getHighestKnowledgeFor(entry) >= PokedexEntryProgress.CAUGHT
            SecondaryPokedexFilterType.SEEN -> pokedexManager.getHighestKnowledgeFor(entry) == PokedexEntryProgress.ENCOUNTERED
            SecondaryPokedexFilterType.UNDISCOVERED -> pokedexManager.getHighestKnowledgeFor(entry) == PokedexEntryProgress.NONE
            SecondaryPokedexFilterType.LEVEL_UP_TM_UNDISCOVERED -> hasUndiscoveredLevelUpTM(entry)
        }
    }

    private fun hasUndiscoveredLevelUpTM(entry: PokedexEntry): Boolean {
        if (pokedexManager.getHighestKnowledgeFor(entry) != PokedexEntryProgress.CAUGHT) return false
        val species = PokemonSpecies.getByIdentifier(entry.speciesId) ?: return false
        val forms = pokedexManager.getCaughtForms(entry)
        val formData = if (forms.isEmpty()) {
            listOf(species.standardForm)
        } else {
            forms.map { form ->
                species.forms.find { it.name.equals(form.displayForm, ignoreCase = true) } ?: species.standardForm
            }
        }

        return formData.any { form ->
            val highestLevel = pokedexManager.getSpeciesRecord(form.species.resourceIdentifier)?.getFormRecord(form.name)?.highestLevel ?: -1
            hasUndiscoveredLevelUpTM(form, highestLevel)
        }
    }

    private fun hasUndiscoveredLevelUpTM(
        form: FormData,
        highestLevel: Int
    ): Boolean {
        val evolutionMoveLevels = buildEvolutionMoveLevelIndex(form)

        fun isLevelUpDiscovered(move: MoveTemplate, level: Int): Boolean {
            if (highestLevel >= level) return true

            for ((evolutionSpeciesId, moveLevels) in evolutionMoveLevels) {
                val evolutionLevel = moveLevels[move.name] ?: continue
                val evolutionHighest = pokedexManager.getSpeciesRecord(evolutionSpeciesId)?.highestLevel ?: -1
                if (evolutionHighest >= evolutionLevel) return true
            }

            return false
        }

        form.moves.levelUpMoves.forEach { (level, moves) ->
            for (move in moves) {
                if (TechnicalMachines.moveToTM[move] != null && !isLevelUpDiscovered(move, level)) {
                    return true
                }
            }
        }

        return false
    }

    private fun buildEvolutionMoveLevelIndex(form: FormData): Map<ResourceLocation, Map<String, Int>> {
        val evolutionForms = collectEvolutionForms(form)
        val levelsBySpecies = mutableMapOf<ResourceLocation, MutableMap<String, Int>>()

        for (evolutionForm in evolutionForms) {
            val speciesId = evolutionForm.species.resourceIdentifier
            val moveLevels = levelsBySpecies.getOrPut(speciesId) { mutableMapOf() }
            buildMoveLevelIndex(evolutionForm).forEach { (moveName, level) ->
                val current = moveLevels[moveName]
                if (current == null || level < current) {
                    moveLevels[moveName] = level
                }
            }
        }

        return levelsBySpecies
    }

    private fun buildMoveLevelIndex(form: FormData): Map<String, Int> {
        val levels = mutableMapOf<String, Int>()
        form.moves.levelUpMoves.forEach { (level, moves) ->
            moves.forEach { move ->
                val current = levels[move.name]
                if (current == null || level < current) {
                    levels[move.name] = level
                }
            }
        }
        return levels
    }

    private fun collectEvolutionForms(rootForm: FormData): List<FormData> {
        val results = mutableListOf<FormData>()
        val visited = mutableSetOf<String>()

        fun key(form: FormData): String {
            return "${form.species.resourceIdentifier}|${form.name.lowercase()}"
        }

        fun nextEvolutions(current: FormData): Set<Evolution> {
            return if (current.evolutions.isNotEmpty()) {
                current.evolutions
            } else {
                current.species.evolutions
            }
        }

        fun traverse(current: FormData) {
            for (evolution in nextEvolutions(current)) {
                val evolutionForm = resolveEvolutionForm(evolution) ?: continue
                val evolutionKey = key(evolutionForm)
                if (!visited.add(evolutionKey)) continue
                results.add(evolutionForm)
                traverse(evolutionForm)
            }
        }

        traverse(rootForm)
        return results
    }

    private fun resolveEvolutionForm(evolution: Evolution): FormData? {
        val speciesId = evolution.result.species?.asIdentifierDefaultingNamespace() ?: return null
        val species = PokemonSpecies.getByIdentifier(speciesId) ?: return null
        val formId = evolution.result.form
        return if (formId == null) {
            species.standardForm
        } else {
            species.forms.firstOrNull {
                it.formOnlyShowdownId().equals(formId, ignoreCase = true) || it.name.equals(formId, ignoreCase = true)
            } ?: species.standardForm
        }
    }
}
