/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.npc.configuration

import com.cobblemon.mod.common.util.DataKeys
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrDefault

class NPCBattleConfiguration {
    var canChallenge = false
    @Deprecated("This is not likely to get used")
    var simultaneousBattles = false
    @Deprecated("This is not likely to get used")
    var healAfterwards = true

    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBoolean(canChallenge)
        buffer.writeBoolean(simultaneousBattles)
        buffer.writeBoolean(healAfterwards)
//        buffer.writeNullable(party) { _, provider ->
//            buffer.writeString(provider.type)
//            provider.encode(buffer)
//        }
    }

    fun decode(buffer: RegistryFriendlyByteBuf) {
        canChallenge = buffer.readBoolean()
        simultaneousBattles = buffer.readBoolean()
        healAfterwards = buffer.readBoolean()
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

    fun save(nbt: ValueOutput) {
        nbt.putBoolean(DataKeys.NPC_CAN_CHALLENGE, canChallenge)
        nbt.putBoolean(DataKeys.NPC_SIMULTANEOUS_BATTLES, simultaneousBattles)
        nbt.putBoolean(DataKeys.NPC_HEAL_AFTERWARDS, healAfterwards)
//        val party = party
//        if (party != null) {
//            val partyNBT = CompoundTag()
//            partyNBT.putString(DataKeys.NPC_PARTY_TYPE, party.type)
//            party.save(partyNBT)
//            nbt.put(DataKeys.NPC_PARTY, partyNBT)
//        }
    }

    fun load(nbt: ValueInput) {
        canChallenge = nbt.getBooleanOr(DataKeys.NPC_CAN_CHALLENGE, false)
        simultaneousBattles = nbt.getBooleanOr(DataKeys.NPC_SIMULTANEOUS_BATTLES, false)
        healAfterwards = nbt.getBooleanOr(DataKeys.NPC_HEAL_AFTERWARDS, true)
//        val partyNBT = nbt.getCompound(DataKeys.NPC_PARTY)
//        if (!partyNBT.isEmpty) {
//            val type = partyNBT.getString(DataKeys.NPC_PARTY_TYPE)
//            val providerBuilder = NPCPartyProvider.types[type]
//                ?: throw IllegalArgumentException("Failed to load NPC party provider of type: $type")
//            party = providerBuilder(type).also { it.load(partyNBT) }
//        }
    }
}
