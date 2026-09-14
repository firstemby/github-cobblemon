/*
 * Copyright (C) 2026 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.api.abilities.Abilities
import com.cobblemon.mod.common.api.moves.Moves
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.stadium.StadiumFormatConfig
import com.cobblemon.mod.common.pokemon.Species
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

object StadiumBanCatalog {
    enum class Category(val key: String) {
        POKEMON("pokemon"), MOVES("moves"), ABILITIES("abilities"), ITEMS("items");

        val label get() = I18n.get("cobblemon.stadium.category.$key")

        fun entries(config: StadiumFormatConfig): MutableSet<String> = when (this) {
            POKEMON -> config.bannedPokemon
            MOVES -> config.bannedMoves
            ABILITIES -> config.bannedAbilities
            ITEMS -> config.bannedItems
        }
    }

    data class Entry(val id: String, val name: Component, val detail: Component, val groups: Set<String> = emptySet(),
        val species: Species? = null, val item: ItemStack = ItemStack.EMPTY, val move: MoveTemplate? = null)

    fun load(category: Category, heldItems: Map<ResourceLocation, String>): List<Entry> = when (category) {
        Category.POKEMON -> PokemonSpecies.species.sortedBy { it.nationalPokedexNumber }.map { species ->
            val types = species.types.joinToString(" / ") { it.displayName.string }
            Entry(species.showdownId(), species.translatedName, Component.translatable("cobblemon.stadium.catalog.species_detail", species.nationalPokedexNumber, types),
                species.types.map { it.displayName.string }.toSet(), species)
        }
        Category.MOVES -> Moves.all().sortedBy { it.displayName.string }.map {
            Entry(it.name, it.displayName, Component.translatable("cobblemon.stadium.catalog.move_detail", it.elementalType.displayName, it.power.toInt(), it.pp)
                .append(it.description), setOf(it.elementalType.displayName.string), move = it)
        }
        Category.ABILITIES -> Abilities.all().sortedBy { Component.translatable(it.displayName).string }.map {
            Entry(it.name, Component.translatable(it.displayName), Component.translatable(it.description))
        }
        Category.ITEMS -> heldItems.mapNotNull { (item, id) ->
            val stack = ItemStack(BuiltInRegistries.ITEM.get(item))
            if (stack.isEmpty) return@mapNotNull null
            val detail = if (I18n.exists("${stack.descriptionId}.tooltip")) Component.translatable("${stack.descriptionId}.tooltip")
                else Component.translatable("cobblemon.stadium.catalog.item_detail", id)
            Entry(id, stack.hoverName, detail, item = stack)
        }.distinctBy { it.id }.sortedBy { it.name.string }
    }
}
