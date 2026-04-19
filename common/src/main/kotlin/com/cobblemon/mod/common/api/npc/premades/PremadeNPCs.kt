/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.premades

import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.npc.NPCClass
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.PremadeNPCRegistrySyncPacket
import com.cobblemon.mod.common.util.adapters.MoValueAdapter
import com.cobblemon.mod.common.util.adapters.NPCClassReferenceAdapter
import com.cobblemon.mod.common.util.adapters.TextAdapter
import com.cobblemon.mod.common.util.adapters.pokemonPropertiesShortAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.fromJson
import com.cobblemon.mod.common.util.server
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.level.storage.LevelResource

object PremadeNPCs : JsonDataRegistry<PremadeNPC> {
    override val id = cobblemonResource("premade_npc")
    override val resourcePath = "premade_npcs"
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<PremadeNPCs>()
    override val typeToken = TypeToken.get(PremadeNPC::class.java)
    override val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .registerTypeAdapter(PokemonProperties::class.java, pokemonPropertiesShortAdapter)
        .registerTypeAdapter(Component::class.java, TextAdapter)
        .registerTypeAdapter(MoValue::class.java, MoValueAdapter)
        .registerTypeAdapter(NPCClass::class.java, NPCClassReferenceAdapter)
        .create()

    val datapackedPremades = mutableMapOf<ResourceLocation, PremadeNPC>()
    val worldSavedPremades = mutableMapOf<ResourceLocation, PremadeNPC>()

    val premadeNPCsById = mutableMapOf<ResourceLocation, PremadeNPC>()
    
    private lateinit var worldSavedPremadeNPCsFolder: File

    fun onServerLoading(server: MinecraftServer) {
        worldSavedPremadeNPCsFolder = server.getWorldPath(LevelResource.ROOT).resolve("premade_npcs").toFile()
        if (!worldSavedPremadeNPCsFolder.exists()) {
            return
        }

        worldSavedPremadeNPCsFolder.walkTopDown().filter { it.isFile && it.extension == "json" }.forEach {
            val identifier = ResourceLocation.parse(it.nameWithoutExtension)
            val premadeNPC = gson.fromJson<PremadeNPC>(it.reader())
            premadeNPC.id = identifier
            premadeNPC.editable = true
            worldSavedPremades[identifier] = premadeNPC
        }
        setupPremadeNPCsById()
        Cobblemon.LOGGER.info("Loaded ${worldSavedPremades.size} world-saved premade NPCs")
    }

    override fun reload(data: Map<ResourceLocation, PremadeNPC>) {
        datapackedPremades.clear()
        datapackedPremades.putAll(data)
        data.entries.forEach { (identifier, premadeNPC) ->
            premadeNPC.id = identifier
            premadeNPC.editable = false
        }
        setupPremadeNPCsById()
        observable.emit(this)
        Cobblemon.LOGGER.info("Loaded ${datapackedPremades.size} datapacked premade NPCs")
    }

    override fun sync(player: ServerPlayer) {
        // re the .toMap(): take a copy of the map now, in case the map is edited while this packet is being encoded on the network thread
        player.sendPacket(PremadeNPCRegistrySyncPacket(premadeNPCsById.toMap()))
    }

    private fun onChangedPremades() {
        setupPremadeNPCsById()
        observable.emit(this)
        server()?.playerList?.players?.forEach(::sync)
    }

    fun savePremadeToWorld(premadeNPC: PremadeNPC) {
        worldSavedPremades[premadeNPC.id] = premadeNPC
        val file = File(worldSavedPremadeNPCsFolder, "${premadeNPC.id.toString().replace(":", "/")}.json")
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(premadeNPC))
        onChangedPremades()
    }

    fun deletePremade(id: ResourceLocation) {
        if (worldSavedPremades.remove(id) == null) {
            return
        }
        val file = File(worldSavedPremadeNPCsFolder, "${id.toString().replace(":", "/")}.json")
        if (file.exists()) {
            file.delete()
        }
        onChangedPremades()
    }

    fun setupPremadeNPCsById() {
        premadeNPCsById.clear()
        premadeNPCsById.putAll(datapackedPremades)
        premadeNPCsById.putAll(worldSavedPremades)
    }
}