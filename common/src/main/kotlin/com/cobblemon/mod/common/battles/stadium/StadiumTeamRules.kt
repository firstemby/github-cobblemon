/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.stadium

import com.cobblemon.mod.common.util.lang
import net.minecraft.network.chat.Component
import com.cobblemon.mod.common.api.pokemon.helditem.HeldItemProvider
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon

object StadiumTeamRules {
    data class Entry(val species: String, val form: String, val level: Int, val moves: List<String>, val ability: String, val item: String)

    fun entries(team: List<BattlePokemon>): List<Entry> = team.map { battlePokemon ->
        val pokemon = battlePokemon.effectedPokemon
        Entry(pokemon.species.showdownId(), pokemon.showdownId(), pokemon.level,
            pokemon.moveSet.getMoves().map { it.name }, pokemon.ability.name,
            HeldItemProvider.provideShowdownId(battlePokemon).orEmpty())
    }

    fun errors(config: StadiumFormatConfig, team: List<Entry>): List<Component> = buildList {
        if (team.size > config.pokemonLimit) add(lang("stadium.team.party_limit", team.size, config.pokemonLimit))
        if (config.levelCap > 0 && team.any { it.level > config.levelCap }) add(lang("stadium.team.level_cap", config.levelCap))
        if ("species_clause" in config.clauses && team.map { id(it.species) }.distinct().size != team.size) add(lang("stadium.team.species_clause"))
        val heldItems = team.map { id(it.item) }.filter(String::isNotEmpty)
        if (!config.allowHeldItems && heldItems.isNotEmpty()) add(lang("stadium.team.held_items"))
        if (config.uniqueHeldItems && heldItems.distinct().size != heldItems.size) add(lang("stadium.team.item_clause"))
        val species = config.bannedPokemon.map(::id).toSet()
        val moves = config.bannedMoves.map(::id).toSet()
        val abilities = config.bannedAbilities.map(::id).toSet()
        val items = config.bannedItems.map(::id).toSet()
        team.forEach {
            if (id(it.species) in species || id(it.form) in species) add(lang("stadium.team.banned_pokemon", it.form))
            if (it.moves.any { move -> id(move) in moves }) add(lang("stadium.team.banned_move", it.form))
            if (id(it.ability) in abilities) add(lang("stadium.team.banned_ability", it.form))
            if (it.item.isNotBlank() && id(it.item) in items) add(lang("stadium.team.banned_item", it.form))
        }
    }

    private fun id(value: String) = StadiumConfiguration.normalizeId(value)
}
