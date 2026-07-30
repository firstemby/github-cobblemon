/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.npc.PartyComposition
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object CobblemonPartyCompositions : JsonDataRegistry<PartyComposition> {
    override val id: ResourceLocation = cobblemonResource("party_compositions")
    override val type = PackType.SERVER_DATA
    override val typeToken: TypeToken<PartyComposition> = TypeToken.get(PartyComposition::class.java)
    override val resourcePath: String = "party_compositions"
    override val observable = SimpleObservable<CobblemonPartyCompositions>()
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .setPrettyPrinting()
        .create()

    val partyCompositions = mutableMapOf<ResourceLocation, PartyComposition>()

    override fun sync(player: ServerPlayer) {
        // probably worth syncing which exist, yeah. the deeper details probably not. I'll get to this eventually.
    }

    override fun reload(data: Map<ResourceLocation, PartyComposition>) {
        data.entries.forEach { it.value.id = it.key }
        partyCompositions.clear()
        partyCompositions.putAll(data)
        observable.emit(this)
        Cobblemon.LOGGER.info("Loaded ${data.size} party compositions.")
    }
}