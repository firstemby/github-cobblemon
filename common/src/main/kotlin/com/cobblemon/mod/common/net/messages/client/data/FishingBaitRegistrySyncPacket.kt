/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.fishing.FishingBait
import com.cobblemon.mod.common.api.fishing.FishingBaits
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class FishingBaitRegistrySyncPacket(fishingBaits: List<FishingBait>) : DataRegistrySyncPacket<FishingBait, FishingBaitRegistrySyncPacket>(fishingBaits) {
    companion object {
        val ID = cobblemonResource("fishing_baits")
        fun decode(buffer: RegistryFriendlyByteBuf) = FishingBaitRegistrySyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID
    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: FishingBait) {
        FishingBait.STREAM_CODEC.encode(buffer, entry)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): FishingBait {
        return FishingBait.STREAM_CODEC.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<FishingBait>) {
        FishingBaits.reload(entries.associateBy { it.item })
    }
}