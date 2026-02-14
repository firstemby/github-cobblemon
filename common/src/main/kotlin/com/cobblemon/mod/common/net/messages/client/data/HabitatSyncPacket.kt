/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.data

import com.cobblemon.mod.common.api.habitats.HabitatPool
import com.cobblemon.mod.common.api.habitats.HabitatPools
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

//class HabitatSyncPacket(habitatPools: List<HabitatPool>) :
//        DataRegistrySyncPacket<HabitatPool, HabitatSyncPacket>(habitatPools) {
//
//    companion object {
//        val ID = cobblemonResource("structure_habitats")
//        fun decode(buffer: RegistryFriendlyByteBuf) =
//                HabitatSyncPacket(emptyList()).apply { decodeBuffer(buffer) }
//    }
//
//    override val id = ID
//
//    override fun encodeEntry(buffer: RegistryFriendlyByteBuf, entry: HabitatPool) {
//        HabitatPool.STREAM_CODEC.encode(buffer, entry)
//    }
//
//    override fun decodeEntry(buffer: RegistryFriendlyByteBuf): HabitatPool {
//        return HabitatPool.STREAM_CODEC.decode(buffer)
//    }
//
//    override fun synchronizeDecoded(entries: Collection<HabitatPool>) {
//        HabitatPools.reload(entries.associateBy { it.structure })
//    }
//}
