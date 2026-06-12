/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common

import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.api.moves.MovesetBuilder
import com.cobblemon.mod.common.api.npc.PartyPool
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.util.ScriptableIntRange
import com.cobblemon.mod.common.util.adapters.ExpressionLikeAdapter
import com.cobblemon.mod.common.util.adapters.IdentifierAdapter
import com.cobblemon.mod.common.util.adapters.IntRangeAdapter
import com.cobblemon.mod.common.util.adapters.MovesetBuilderReferenceAdapter
import com.cobblemon.mod.common.util.adapters.PokemonPropertiesAdapter
import com.cobblemon.mod.common.util.adapters.ScriptableIntRangeAdapter
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType

object CobblemonPartyPools : JsonDataRegistry<PartyPool> {
    override val id: ResourceLocation = cobblemonResource("party_pools")
    override val type = PackType.SERVER_DATA
    override val typeToken: TypeToken<PartyPool> = TypeToken.get(PartyPool::class.java)
    override val resourcePath: String = "party_pools"
    override val observable = SimpleObservable<CobblemonPartyPools>()
    override val gson: Gson = GsonBuilder()
        .registerTypeAdapter(ResourceLocation::class.java, IdentifierAdapter)
        .registerTypeAdapter(PokemonProperties::class.java, PokemonPropertiesAdapter(saveLong = false))
        .registerTypeAdapter(IntRange::class.java, IntRangeAdapter)
        .registerTypeAdapter(ScriptableIntRange::class.java, ScriptableIntRangeAdapter)
        .registerTypeAdapter(MovesetBuilder::class.java, MovesetBuilderReferenceAdapter)
        .registerTypeAdapter(ExpressionLike::class.java, ExpressionLikeAdapter)
        .setPrettyPrinting()
        .create()

    val partyPools = mutableMapOf<ResourceLocation, PartyPool>()

    override fun sync(player: ServerPlayer) {
        // probably worth syncing. Knowing the details might help exploit the server? lot of effort for little though.
    }

    override fun reload(data: Map<ResourceLocation, PartyPool>) {
        data.entries.forEach { it.value.id = it.key }
        partyPools.clear()
        partyPools.putAll(data)
        observable.emit(this)
    }
}