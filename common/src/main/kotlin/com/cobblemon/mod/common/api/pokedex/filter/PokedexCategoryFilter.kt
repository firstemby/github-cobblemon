/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.filter

import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace

enum class PokedexCategoryFilterType {
    ALL,
    OWNED,
    SEEN,
    UNREGISTERED,
    UNDISCOVERED_TM_MOVE,
    RIDEABLE
}

class PokedexCategoryFilter(
    private val pokedexManager: AbstractPokedexManager,
    private val filterType: PokedexCategoryFilterType
) : EntryFilter() {

    override fun test(entry: PokedexEntry): Boolean {
        return when (filterType) {
            PokedexCategoryFilterType.ALL -> true
            PokedexCategoryFilterType.OWNED -> pokedexManager.getHighestKnowledgeFor(entry) >= PokedexEntryProgress.OWNED
            PokedexCategoryFilterType.SEEN -> pokedexManager.getHighestKnowledgeFor(entry) >= PokedexEntryProgress.OWNED || pokedexManager.getHighestKnowledgeFor(entry) == PokedexEntryProgress.SEEN
            PokedexCategoryFilterType.UNREGISTERED -> pokedexManager.getHighestKnowledgeFor(entry) == PokedexEntryProgress.UNREGISTERED
            PokedexCategoryFilterType.UNDISCOVERED_TM_MOVE -> hasUndiscoveredLevelUpTM(entry)
            PokedexCategoryFilterType.RIDEABLE -> {
                val species = PokemonSpecies.getByIdentifier(entry.speciesId)
                species?.forms?.any { form -> !form.riding.behaviours.isNullOrEmpty() } == true
                    && pokedexManager.getHighestKnowledgeFor(entry) >= PokedexEntryProgress.OWNED
            }
        }
    }

    private fun hasUndiscoveredLevelUpTM(entry: PokedexEntry): Boolean {
        if (pokedexManager.getHighestKnowledgeFor(entry) != PokedexEntryProgress.OWNED) return false
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
        val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
        val highestEvolutionLevel = collectEvolutionForms(form).maxOfOrNull { evolutionForm ->
            val speciesRecord = pokedexManager.getSpeciesRecord(evolutionForm.species.resourceIdentifier)
            speciesRecord?.getFormRecord(evolutionForm.name)?.highestLevel ?: speciesRecord?.highestLevel ?: -1
        } ?: -1

        fun isLevelUpDiscovered(level: Int): Boolean {
            if (highestLevel >= level) return true
            return highestEvolutionLevel >= level
        }

        form.moves.levelUpMoves.forEach { (level, moves) ->
            for (move in moves) {
                val tmId = TechnicalMachines.moveToTM[move]?.id ?: continue
                val tmUnlocked = tmId in learnedTMs || TechnicalMachines.tmMap[tmId]?.isPassivelyObtained() == true
                if (!tmUnlocked && !isLevelUpDiscovered(level)) {
                    return true
                }
            }
        }

        return false
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
