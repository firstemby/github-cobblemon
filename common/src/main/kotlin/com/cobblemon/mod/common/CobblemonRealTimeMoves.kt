/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.bedrockk.molang.Expression
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.pokemon.ai.RealTimeMove
import com.cobblemon.mod.common.util.adapters.ExpressionAdapter
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object CobblemonRealTimeMoves : JsonDataRegistry<RealTimeMove> {
    override val type = PackType.SERVER_DATA
    override val observable = SimpleObservable<CobblemonRealTimeMoves>()
    override val typeToken = TypeToken.get(RealTimeMove::class.java)
    override val resourcePath: String = "real_time_moves"
    override val id: ResourceLocation = cobblemonResource(resourcePath)
    override val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .registerTypeAdapter(Expression::class.java, ExpressionAdapter)
        .create()

    val realTimeMoves = mutableMapOf<ResourceLocation, RealTimeMove>()

    override fun reload(data: Map<ResourceLocation, RealTimeMove>) {
        realTimeMoves.clear()
        data.forEach { location, move -> move.id = location }
        realTimeMoves.putAll(data)
    }

    override fun sync(player: ServerPlayer) {
        // nah
    }
}