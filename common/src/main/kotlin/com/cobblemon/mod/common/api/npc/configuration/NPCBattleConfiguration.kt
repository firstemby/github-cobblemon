/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration

import com.cobblemon.mod.common.util.DataKeys
import com.cobblemon.mod.common.util.asIdentifierDefaultingNamespace
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class NPCBattleConfiguration {
    var canChallenge = false
    @Deprecated("This is not likely to get used")
    var simultaneousBattles = false
    @Deprecated("This is not likely to get used")
    var healAfterwards = true
    /** The resource location of the battle AI configuration to use. If null, falls back to skill level. */
    var battleAIConfigId: ResourceLocation? = null
    /** The resource location of the battle bag configuration to use. If null, no items are used. */
    var battleBagId: ResourceLocation? = null

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(canChallenge)
        buffer.writeBoolean(simultaneousBattles)
        buffer.writeBoolean(healAfterwards)
        buffer.writeNullable(battleAIConfigId) { buf, id -> buf.writeResourceLocation(id) }
        buffer.writeNullable(battleBagId) { buf, id -> buf.writeResourceLocation(id) }
//        buffer.writeNullable(party) { _, provider ->
//            buffer.writeString(provider.type)
//            provider.encode(buffer)
//        }
    }

    fun decode(buffer: RegistryFriendlyByteBuf) {
        canChallenge = buffer.readBoolean()
        simultaneousBattles = buffer.readBoolean()
        healAfterwards = buffer.readBoolean()
        battleAIConfigId = buffer.readNullable { it.readResourceLocation() }
        battleBagId = buffer.readNullable { it.readResourceLocation() }
//        party = buffer.readNullable {
//            val type = buffer.readString()
//            val providerBuilder = NPCPartyProvider.types[type]
//                ?: throw IllegalArgumentException("Failed to load NPC party provider of type: $type")
//
//            val decodedParty = providerBuilder(type)
//            decodedParty.decode(buffer)
//            decodedParty
//        }
    }

    fun saveToNBT(nbt: CompoundTag) {
        nbt.putBoolean(DataKeys.NPC_CAN_CHALLENGE, canChallenge)
        nbt.putBoolean(DataKeys.NPC_SIMULTANEOUS_BATTLES, simultaneousBattles)
        nbt.putBoolean(DataKeys.NPC_HEAL_AFTERWARDS, healAfterwards)
        battleAIConfigId?.let { nbt.putString(DataKeys.NPC_AI_CONFIG, it.toString()) }
        battleBagId?.let { nbt.putString(DataKeys.NPC_BATTLE_BAG, it.toString()) }
//        val party = party
//        if (party != null) {
//            val partyNBT = CompoundTag()
//            partyNBT.putString(DataKeys.NPC_PARTY_TYPE, party.type)
//            party.saveToNBT(partyNBT)
//            nbt.put(DataKeys.NPC_PARTY, partyNBT)
//        }
    }

    fun loadFromNBT(nbt: CompoundTag) {
        canChallenge = nbt.getBoolean(DataKeys.NPC_CAN_CHALLENGE)
        simultaneousBattles = nbt.getBoolean(DataKeys.NPC_SIMULTANEOUS_BATTLES)
        healAfterwards = nbt.getBoolean(DataKeys.NPC_HEAL_AFTERWARDS)
        if (nbt.contains(DataKeys.NPC_AI_CONFIG)) {
            battleAIConfigId = try { nbt.getString(DataKeys.NPC_AI_CONFIG).asIdentifierDefaultingNamespace() } catch (_: Exception) { null }
        }
        if (nbt.contains(DataKeys.NPC_BATTLE_BAG)) {
            battleBagId = try { nbt.getString(DataKeys.NPC_BATTLE_BAG).asIdentifierDefaultingNamespace() } catch (_: Exception) { null }
        }
//        val partyNBT = nbt.getCompound(DataKeys.NPC_PARTY)
//        if (!partyNBT.isEmpty) {
//            val type = partyNBT.getString(DataKeys.NPC_PARTY_TYPE)
//            val providerBuilder = NPCPartyProvider.types[type]
//                ?: throw IllegalArgumentException("Failed to load NPC party provider of type: $type")
//            party = providerBuilder(type).also { it.loadFromNBT(partyNBT) }
//        }
    }
}