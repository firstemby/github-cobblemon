/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.pokenav

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readIdentifier
import com.cobblemon.mod.common.util.readUUID
import com.cobblemon.mod.common.util.writeCollection
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeUUID
import java.util.UUID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class PokeNavAreaScanDataPacket(
    val scanRadius: Float,
    val entries: List<AreaScanEntry>
) : NetworkPacket<PokeNavAreaScanDataPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeFloat(scanRadius)
        buffer.writeCollection(entries) { buf, entry ->
            buf.writeUUID(entry.entityUuid)
            buf.writeIdentifier(entry.speciesId)
            buf.writeFloat(entry.distance)
        }
    }

    companion object {
        val ID = cobblemonResource("pokenav_area_scan_data")

        fun decode(buffer: RegistryFriendlyByteBuf): PokeNavAreaScanDataPacket {
            val radius = buffer.readFloat()
            val entries = buffer.readList { buf ->
                AreaScanEntry(
                    entityUuid = buf.readUUID(),
                    speciesId = buf.readIdentifier(),
                    distance = buf.readFloat()
                )
            }
            return PokeNavAreaScanDataPacket(radius, entries)
        }
    }

    data class AreaScanEntry(
        val entityUuid: UUID,
        val speciesId: ResourceLocation,
        val distance: Float
    )
}
