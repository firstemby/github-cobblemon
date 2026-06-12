/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.partyproviders

import com.bedrockk.molang.Expression
import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.DoubleValue
import com.cobblemon.mod.common.CobblemonPartyCompositions
import com.cobblemon.mod.common.CobblemonPartyPools
import com.cobblemon.mod.common.api.molang.MoLangFunctions.asMoLangValue
import com.cobblemon.mod.common.api.molang.MoLangFunctions.setup
import com.cobblemon.mod.common.api.npc.NPCPartyProvider
import com.cobblemon.mod.common.api.npc.PartyComposition
import com.cobblemon.mod.common.api.npc.PartyPool
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.util.asArrayValue
import com.cobblemon.mod.common.util.asExpression
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.resolveInt
import com.cobblemon.mod.common.util.withQueryValue
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlin.random.Random
import kotlin.ranges.random
import net.minecraft.server.level.ServerPlayer

/**
 * An [NPCPartyProvider] which uses a [PartyComposition] and [PartyPool] to generate a party.
 *
 * There are controls for minimum and maximum Pokémon count, as well as whether to use a fixed random seed for the NPC.
 *
 * @author Hiroku
 * @since December 8th, 2025
 */
class ComposedPoolPartyProvider : NPCPartyProvider {
    companion object {
        val TYPE = cobblemonResource("composed_pool")
    }

    override val type = TYPE
    override var isStatic: Boolean = true
    var useFixedRandom: Boolean = false
    var minPokemon: Expression = "1".asExpression()
    var maxPokemon: Expression = "6".asExpression()
    lateinit var pool: PartyPool
    lateinit var composition: PartyComposition

    override fun loadFromJSON(json: JsonElement) {
        json as JsonObject
        isStatic = json.getAsJsonPrimitive("isStatic")?.asBoolean ?: true
        useFixedRandom = json.getAsJsonPrimitive("useFixedRandom")?.asBoolean ?: false
        pool = CobblemonPartyPools.partyPools[json.get("pool").asString.asIdentifierDefaultingNamespace()]
            ?: throw IllegalArgumentException("Unknown PartyPool id: ${json.get("pool").asString}")
        composition = CobblemonPartyCompositions.partyCompositions[json.get("composition").asString.asIdentifierDefaultingNamespace()]
            ?: throw IllegalArgumentException("Unknown PartyComposition id: ${json.get("composition").asString}")
        minPokemon = json.getAsJsonPrimitive("minPokemon").asString?.asExpression() ?: "1".asExpression()
        maxPokemon = json.getAsJsonPrimitive("maxPokemon").asString?.asExpression() ?: "6".asExpression()
    }

    override fun provide(npc: NPCEntity, level: Int, players: List<ServerPlayer>): NPCPartyStore {
        val runtime = MoLangRuntime().setup().withQueryValue("npc", npc.struct)
        val random = if (useFixedRandom) Random(npc.uuid.hashCode()) else Random.Default
        runtime.withQueryValue("level", DoubleValue(level))
        runtime.withQueryValue("players", players.asArrayValue { it.asMoLangValue() })
        if (players.size == 1) {
            // This is for the convenience, most cases will be pvn one player
            runtime.withQueryValue("player", players.first().asMoLangValue())
        }
        val minPokemon = runtime.resolveInt(this.minPokemon)
        val maxPokemon = runtime.resolveInt(this.maxPokemon)
        val desiredPokemonCount = (minPokemon..maxPokemon).random(random)

        val pokemonList = composition.compose(
            pool = pool,
            level = level,
            aspects = npc.aspects,
            desiredPokemonCount = desiredPokemonCount,
            runtime = runtime,
            random = random
        )

        val partyStore = NPCPartyStore(npc)
        pokemonList.forEach(partyStore::add)
        return partyStore
    }
}