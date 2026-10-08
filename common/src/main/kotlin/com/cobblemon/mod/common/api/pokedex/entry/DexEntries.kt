/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.entry

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.DexEntrySyncPacket
import com.cobblemon.mod.common.net.messages.client.data.DexEvolutionDisplaySyncPacket
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.item.Item

object DexEntries : JsonDataRegistry<PokedexEntry> {
    override val id = cobblemonResource("dex_entries")
    override val type = PackType.SERVER_DATA

    override val gson: Gson = GsonBuilder()
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .create()

    override val typeToken: TypeToken<PokedexEntry> = TypeToken.get(PokedexEntry::class.java)
    override val resourcePath = "dex_entries"

    val entries = mutableMapOf<ResourceLocation, PokedexEntry>()

    val evolutionDisplays = mutableListOf<DexEvolutionDisplay>()

    override fun reload(data: Map<ResourceLocation, PokedexEntry>) {
        data.forEach { _, entry ->
            entries[entry.id] = entry
            if (entry.forms.isEmpty()) {
                entry.forms.add(PokedexForm())
            }
            entry.forms.forEach {
                if (it.unlockForms.isEmpty()) {
                    it.unlockForms = mutableSetOf(it.displayForm)
                }
            }
        }
        computeEvolutionDisplays()
        observable.emit(this)
    }

    override val observable = SimpleObservable<DexEntries>()

    override fun sync(player: ServerPlayer) {
        DexEntrySyncPacket(entries.values).sendToPlayer(player)
        DexEvolutionDisplaySyncPacket(evolutionDisplays).sendToPlayer(player)
    }

    fun computeEvolutionDisplays() {
        evolutionDisplays.clear()
        PokemonSpecies.species.forEach {
            evolutionDisplays.addAll(buildDisplays(it.standardForm))
            evolutionDisplays.addAll(it.forms.flatMap { form -> buildDisplays(form) })
        }
    }

    fun buildDisplays(form: FormData): List<DexEvolutionDisplay> {
        val result = mutableListOf<DexEvolutionDisplay>()
        (form.ownEvolutions ?: form.evolutions).forEach { evolution ->
            val resultPokemon = evolution.result.create()
            val icons = mutableListOf<Item>()
            val tooltips = mutableListOf<Component>()
            getToolTipHeaderIcon(evolution)?.let { header ->
                icons.add(header.first)
                tooltips.add(header.second)
            }
            evolution.requirements.mapNotNull { resolveRequirementData(it) }.forEach { requirement ->
                icons.add(requirement.icon)
                tooltips.add(requirement.tooltip)
            }

            result.add(DexEvolutionDisplay(
                fromSpecies = form.species.resourceIdentifier,
                fromForm = form.name,
                fromAspects = form.aspects.toSet(),
                toSpecies = resultPokemon.species.resourceIdentifier,
                toForm = resultPokemon.form.name,
                toAspects = resultPokemon.aspects,
                icons = icons,
                toolTips = tooltips,
            ))
        }
        return result
    }
}