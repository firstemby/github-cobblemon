/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex.entry

import com.cobblemon.mod.common.CobblemonItems
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.DexEntrySyncPacket
import com.cobblemon.mod.common.net.messages.client.data.DexEvolutionDisplaySyncPacket
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

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
            val requirements = mutableListOf<DexEvolutionRequirementDisplay>()
            getToolTipHeaderIcon(evolution)?.let { header ->
                requirements.add(header)
            }
            evolution.requirements.mapNotNull { resolveRequirementData(it) }.forEach { requirement ->
                requirements.add(requirement)
            }

            listOfNotNull(evolution.result, evolution.shedder).forEach resultLoop@{ resultProperties ->
                val resultSpecies = resultProperties.species
                    ?.let { PokemonSpecies.getByIdentifier(it.asIdentifierDefaultingNamespace()) }
                    ?: return@resultLoop

                if (!resultSpecies.implemented)
                    return@resultLoop

                // Carry the source form's aspects over like a real evolution does (Alola-Bias Pichu -> Alola-Bias Pikachu)
                val resultForm = resultSpecies.getForm(form.aspects.toSet() + resultProperties.aspects)
                val toAspects = resultProperties.aspects + resultForm.aspects
                val resultRequirements = if (resultProperties === evolution.shedder) {
                    requirements + CobblemonDexEvolutionRequirementDisplay(CobblemonItems.POKE_BALL, lang("ui.pokedex.evolution.shedder.tooltip"))
                } else {
                    requirements
                }

                result.add(DexEvolutionDisplay(
                    fromSpecies = form.species.resourceIdentifier,
                    fromForm = form.name,
                    fromAspects = form.aspects.toSet(),
                    toSpecies = resultSpecies.resourceIdentifier,
                    toForm = resultForm.name,
                    toAspects = toAspects,
                    requirements = resultRequirements,
                ))
            }
        }
        return result
    }
}