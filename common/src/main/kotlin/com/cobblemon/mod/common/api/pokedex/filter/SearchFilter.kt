/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.filter

import com.cobblemon.mod.common.api.drop.ItemDropEntry
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokedex.AbstractPokedexManager
import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.evolution.Evolution
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.pokemon.abilities.HiddenAbility
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.itemRegistry
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/**
 * A Pokedex [EntryFilter] that filters out entries that do not contain the current search.
 *
 * @author whatsy
 * @since September 4th, 2024
 * @param searchString The string to use when checking.
 */
class SearchFilter(val pokedexManager: AbstractPokedexManager, val searchString: String, val searchByType: SearchByType = SearchByType.SPECIES) : EntryFilter() {

    override fun test(entry: PokedexEntry): Boolean {
        if (searchString == "") return true

        val species = PokemonSpecies.getByIdentifier(entry.speciesId) ?: return false
        val highestKnowledgeForEntry = pokedexManager.getHighestKnowledgeFor(entry)
        if (highestKnowledgeForEntry == PokedexEntryProgress.UNREGISTERED) return false

        when (searchByType) {
            SearchByType.ABILITIES -> {
                if (pokedexManager.getHighestKnowledgeFor(entry) !== PokedexEntryProgress.OWNED) return false
                val abilityList = mutableListOf<String>()
                val formsList = if (species.forms.isEmpty()) mutableListOf(species.standardForm) else species.forms
                formsList.forEach {
                    it.abilities.sortedBy { it is HiddenAbility }.map { ability -> ability.template }.forEach {
                        abilityList.add(it.displayName.asTranslated().string.lowercase())
                    }
                }
                return abilityList.any { it.contains(searchString.trim().lowercase()) }
            }
            SearchByType.MOVES -> {
                if (pokedexManager.getHighestKnowledgeFor(entry) !== PokedexEntryProgress.OWNED) return false
                val search = searchString.trim().lowercase()
                val learnedTMs = CobblemonClient.clientTMMoveData.learnedTMs
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
                    hasDiscoveredMove(form, search, highestLevel, pokedexManager, learnedTMs)
                }
            }
            SearchByType.DROPS -> {
                if (pokedexManager.getHighestKnowledgeFor(entry) !== PokedexEntryProgress.OWNED) return false
                val dropsList = mutableListOf<String>()
                val formsList = if (species.forms.isEmpty()) mutableListOf(species.standardForm) else species.forms
                formsList.forEach {
                    it.drops.entries.forEach {
                        if (it is ItemDropEntry) {
                            val itemStack = Minecraft.getInstance().player?.level()?.itemRegistry?.get(it.item)?.defaultInstance ?: ItemStack.EMPTY
                            if (!itemStack.isEmpty) dropsList.add(itemStack.displayName.string.lowercase())
                        }
                    }
                }
                return dropsList.any { it.contains(searchString.trim().lowercase()) }
            }
            // Search by species name
            else -> {
                return species.translatedName.string.contains(searchString.trim(), true)
            }
        }
    }

    private fun hasDiscoveredMove(
        form: FormData,
        search: String,
        highestLevel: Int,
        pokedex: AbstractPokedexManager,
        learnedTMs: Set<ResourceLocation>
    ): Boolean {
        val highestEvolutionLevel = collectEvolutionForms(form).maxOfOrNull { evolutionForm ->
            val speciesRecord = pokedex.getSpeciesRecord(evolutionForm.species.resourceIdentifier)
            speciesRecord?.getFormRecord(evolutionForm.name)?.highestLevel ?: speciesRecord?.highestLevel ?: -1
        } ?: -1

        fun matches(move: MoveTemplate): Boolean {
            return move.displayName.string.lowercase().contains(search)
        }

        fun isLevelUpDiscovered(level: Int): Boolean {
            if (highestLevel >= level) return true
            return highestEvolutionLevel >= level
        }

        form.moves.levelUpMoves.forEach { (level, moves) ->
            for (move in moves) {
                if (matches(move) && isLevelUpDiscovered(level)) return true
            }
        }

        form.moves.tmMoves.forEach { move ->
            val tmId = TechnicalMachines.moveToTM[move]?.id
            val discovered = tmId == null
                || tmId in learnedTMs
                || TechnicalMachines.tmMap[tmId]?.isPassivelyObtained() == true
            if (discovered && matches(move)) return true
        }

        val alwaysDiscovered = sequenceOf(
            form.moves.tutorMoves,
            form.moves.eggMoves,
            form.moves.evolutionMoves,
            form.moves.formChangeMoves,
            form.moves.specialMoves,
            form.moves.legacyMoves
        )

        for (moves in alwaysDiscovered) {
            for (move in moves) {
                if (matches(move)) return true
            }
        }

        return false
    }

    // todo remove this since it isn't needed anymore
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
