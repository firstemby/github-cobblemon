/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.tms.TechnicalMachines
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.settings.ServerSettings
import net.minecraft.resources.ResourceLocation

/**
 * Backing data for the MoveDex: which moves the player has discovered (aggregated across every seen
 * species' learnset, learned TMs, or all moves when the server unlocks the dex), and the reverse
 * learnset (for a given move, which species can learn it and by what method).
 */
object MoveDexData {

    enum class LearnMethod(val order: Int, val key: String, val fallback: String) {
        LEVEL(0, "level", "Level"),
        TM(1, "tm", "TM"),
        EGG(2, "egg", "Egg"),
        TUTOR(3, "tutor", "Tutor")
    }

    class LearnEntry(val speciesId: ResourceLocation, val method: LearnMethod, val level: Int?)

    /** move -> the species/method combinations that can learn it (built once from all species). */
    private val reverseIndex: Map<MoveTemplate, List<LearnEntry>> by lazy {
        runCatching { buildReverseIndex() }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to build MoveDex reverse learnset", it)
            emptyMap()
        }
    }

    private var discoveredCache: Set<MoveTemplate>? = null

    /** Recomputes the discovered-move set; call when the MoveDex is opened. */
    fun refreshDiscovered() {
        discoveredCache = runCatching { computeDiscovered() }.getOrElse {
            Cobblemon.LOGGER.warn("Failed to compute discovered moves", it)
            emptySet()
        }
    }

    fun isDiscovered(move: MoveTemplate): Boolean = (discoveredCache ?: emptySet()).contains(move)

    fun discoveredCount(): Int = discoveredCache?.size ?: 0

    /** Total number of moves in the game (the MoveDex denominator). */
    fun totalCount(): Int = Moves.all().count()

    fun learnedBy(move: MoveTemplate): List<LearnEntry> = reverseIndex[move] ?: emptyList()

    /** Whether a species has been seen (has any pokédex record), so its name can be shown un-redacted. */
    fun isSpeciesSeen(speciesId: ResourceLocation): Boolean =
        CobblemonClient.clientPokedexData.speciesRecords.containsKey(speciesId)

    private fun computeDiscovered(): Set<MoveTemplate> {
        if (ServerSettings.unlockAllMoveDexMovesByDefault) return Moves.all().toSet()
        val discovered = HashSet<MoveTemplate>()
        // Every move any seen species can learn counts as discovered.
        for (speciesId in CobblemonClient.clientPokedexData.speciesRecords.keys) {
            val species = PokemonSpecies.getByIdentifier(speciesId) ?: continue
            for (form in species.forms) {
                val learnset = form.moves
                learnset.levelUpMoves.values.forEach(discovered::addAll)
                discovered.addAll(learnset.eggMoves)
                discovered.addAll(learnset.tutorMoves)
                discovered.addAll(learnset.tmMoves)
            }
        }
        // Plus any move whose TM the player has learned.
        for ((move, tm) in TechnicalMachines.moveToTM) {
            if (tm.id in CobblemonClient.clientTMMoveData.learnedTMs) discovered.add(move)
        }
        return discovered
    }

    private fun buildReverseIndex(): Map<MoveTemplate, List<LearnEntry>> {
        val map = HashMap<MoveTemplate, LinkedHashMap<String, LearnEntry>>()
        fun record(move: MoveTemplate, speciesId: ResourceLocation, method: LearnMethod, level: Int?) {
            val byKey = map.getOrPut(move) { LinkedHashMap() }
            // Dedup per species+method; keep the lowest level for level-up entries.
            val key = "${speciesId}|${method.name}"
            val existing = byKey[key]
            if (existing == null || (level != null && existing.level != null && level < existing.level)) {
                byKey[key] = LearnEntry(speciesId, method, level)
            }
        }
        for (species in PokemonSpecies.species) {
            val speciesId = species.resourceIdentifier
            for (form in species.forms) {
                val learnset = form.moves
                learnset.levelUpMoves.forEach { (level, moves) -> moves.forEach { record(it, speciesId, LearnMethod.LEVEL, level) } }
                learnset.tmMoves.forEach { record(it, speciesId, LearnMethod.TM, null) }
                learnset.eggMoves.forEach { record(it, speciesId, LearnMethod.EGG, null) }
                learnset.tutorMoves.forEach { record(it, speciesId, LearnMethod.TUTOR, null) }
            }
        }
        return map.mapValues { (_, byKey) ->
            byKey.values.sortedWith(compareBy({ it.method.order }, { it.level ?: 0 }))
        }
    }
}
