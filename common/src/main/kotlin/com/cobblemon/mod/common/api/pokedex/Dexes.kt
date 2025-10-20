/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.pokedex

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.pokedex.def.PokedexDef
import com.cobblemon.mod.common.api.pokedex.entry.PokedexEntry
import com.cobblemon.mod.common.api.pokedex.entry.PokedexForm
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.PokedexDexSyncPacket
import com.cobblemon.mod.common.util.adapters.CodecBackedAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object Dexes : JsonDataRegistry<PokedexDef> {
    override val id = cobblemonResource("dexes")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<Dexes>()

    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(PokedexDef::class.java, CodecBackedAdapter(PokedexDef.CODEC))
        .disableHtmlEscaping()
        .setPrettyPrinting()
        .create()

    override val typeToken: TypeToken<PokedexDef> = TypeToken.get(PokedexDef::class.java)
    override val resourcePath = "dexes"

    //Maps a dex id to its PokedexDef
    val dexEntryMap = linkedMapOf<ResourceLocation, PokedexDef>()

    val equivalentForms = mapOf(
        "normal" to listOf("normal"),
        "galar" to listOf("bias-galar", "galar-bias", "galarian", "galar"),
        "alola" to listOf("bias-alola", "alola-bias", "alolan", "alola"),
        "hisui" to listOf("bias-hisui", "hisui-bias", "hisuian", "hisui"),
        "paldea" to listOf("bias-paldea", "paldea-bias", "paldean", "paldea"),
    )

    val equivalentFormsLookup = equivalentForms.flatMap { (key, values) ->
        values.map { it to key }
    }.toMap()

    override fun reload(data: Map<ResourceLocation, PokedexDef>) {
        dexEntryMap.clear()
        data.entries.sortedBy { it.value.sortOrder }.forEach { (id, def) -> dexEntryMap[id] = def }
        observable.emit(this)
    }

    override fun sync(player: ServerPlayer) {
        PokedexDexSyncPacket(dexEntryMap.values).sendToPlayer(player)
    }

    fun findDexEntryBySpeciesId(regionId: ResourceLocation, speciesId: ResourceLocation): PokedexEntry? {
        return dexEntryMap[regionId]?.getEntries()?.firstOrNull { it -> it.speciesId == speciesId }
    }

    fun findDexEntryBySpeciesId(speciesId: ResourceLocation): PokedexEntry? {
        return findDexEntryBySpeciesId(cobblemonResource("national"), speciesId)
    }

    fun findRootDexEntryBySpeciesId(speciesId: ResourceLocation, formName: String): Pair<PokedexEntry, PokedexForm> {
        var rootSpecies = speciesId.let { PokemonSpecies.getByIdentifier(it) }!!
        while (rootSpecies.preEvolution != null) {
            rootSpecies = rootSpecies.preEvolution!!.species
        }

        val entry = findDexEntryBySpeciesId(rootSpecies.resourceIdentifier)
            ?: throw RuntimeException("Root dex entry not found")

        val formEquivalents = getFormEquivalents(formName)
        val form = entry.forms.first {
            formEquivalents.contains(it.displayForm.lowercase())
        }

        return entry to form
    }

    fun getFormEquivalents(formName: String): List<String> {
        return equivalentForms[equivalentFormsLookup[formName.lowercase()]]!!
    }
}