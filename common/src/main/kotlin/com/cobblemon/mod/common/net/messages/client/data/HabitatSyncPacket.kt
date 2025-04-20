/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.habitats.Habitat
import com.cobblemon.mod.common.api.habitats.Habitats
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class HabitatSyncPacket(habitats: List<Habitat>) :
        DataRegistrySyncPacket<Habitat, HabitatSyncPacket>(habitats) {

    companion object {
        val ID = cobblemonResource("structure_habitats")
        fun decode(buffer: RegistryFriendlyByteBuf) =
                HabitatSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
    }

    override val id = ID

    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: Habitat) {
        Habitat.STREAM_CODEC.encode(buffer, entry)
    }

    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): Habitat {
        return Habitat.STREAM_CODEC.decode(buffer)
    }

    override fun synchronizeDecoded(entries: Collection<Habitat>) {
        Habitats.reload(entries.associateBy { it.structure })
    }
}
