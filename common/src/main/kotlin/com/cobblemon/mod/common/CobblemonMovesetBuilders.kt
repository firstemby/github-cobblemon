/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.moves.MoveSelector
import com.cobblemon.mod.common.api.moves.MovesetBuilder
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.MovesetBuilderAdapter
import com.cobblemon.mod.common.util.adapters.StringToObjectAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object CobblemonMovesetBuilders : JsonDataRegistry<MovesetBuilder> {
    override val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(MovesetBuilder::class.java, MovesetBuilderAdapter)
        .registerTypeAdapter(MoveSelector::class.java, StringToObjectAdapter(MoveSelector.selectors))
        .create()

    override val typeToken = TypeToken.get(MovesetBuilder::class.java)
    override val resourcePath = "moveset_builders"
    override val id: ResourceLocation = cobblemonResource("moveset_builders")
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<CobblemonMovesetBuilders>()

    val movesetBuilders = mutableMapOf<ResourceLocation, MovesetBuilder>()

    fun getOrThrow(id: ResourceLocation): MovesetBuilder {
        return movesetBuilders[id]
            ?: throw IllegalArgumentException("Unknown MovesetBuilder id: $id")
    }

    override fun sync(player: ServerPlayer) { /* These don't sync. But what if they did? Nah probably shouldn't sync. */ }

    override fun reload(data: Map<ResourceLocation, MovesetBuilder>) {
        movesetBuilders.clear()
        data.forEach { (id, value) -> value.id = id }
        movesetBuilders.putAll(data)
        observable.emit(this)
    }
}