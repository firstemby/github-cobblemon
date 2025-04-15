/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.habitats

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.net.messages.client.data.HabitatSyncPacket
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object Habitats : JsonDataRegistry<Habitat> {
    override val id = cobblemonResource("habitats")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<Habitats>()
    override val typeToken: TypeToken<Habitat> = TypeToken.get(Habitat::class.java)
    override val resourcePath = "habitats"
    override val gson: Gson = GsonBuilder()
            .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
            .setPrettyPrinting()
            .create()

    private val table = mutableMapOf<ResourceLocation, Habitat>()

    override fun sync(player: ServerPlayer) {
        HabitatSyncPacket(table.values.toList()).sendToPlayer(player)
    }

    override fun reload(data: Map<ResourceLocation, Habitat>) {
        table.clear()
        table.putAll(data)

        println("[Habitats] Reloaded ${table.size} habitat(s):")
        for ((structureId, habitat) in table) {
            println("  - ${structureId}:")
            if (habitat.spawnPool.isEmpty()) {
                println("      (No spawn entries)")
            } else {
                habitat.spawnPool.forEachIndexed { i, group ->
                    println("      • Group $i:")
                    group.forEach { entry ->
                        println("          - $entry")
                    }
                }
            }
        }
    }

    fun getHabitat(structureId: ResourceLocation): Habitat? {
        return table[structureId]
    }

    fun getSpawnOptions(structureId: ResourceLocation): List<ResourceLocation> {
        return table[structureId]?.spawnPool?.flatten() ?: emptyList()
    }
}
