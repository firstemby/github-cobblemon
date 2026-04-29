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
import com.cobblemon.mod.common.util.readString
import com.cobblemon.mod.common.util.writeCollection
import com.cobblemon.mod.common.util.writeIdentifier
import com.cobblemon.mod.common.util.writeString
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class PokeNavSpawnDataPacket(
    val buckets: List<String>,
    val selectedBucket: String,
    val entries: List<SpawnChanceEntry>
) : NetworkPacket<PokeNavSpawnDataPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(buckets) { buf, bucket -> buf.writeString(bucket) }
        buffer.writeString(selectedBucket)
        buffer.writeCollection(entries) { buf, entry ->
            buf.writeIdentifier(entry.speciesId)
            buf.writeFloat(entry.chance)
        }
    }

    companion object {
        val ID = cobblemonResource("pokenav_spawn_data")

        fun decode(buffer: RegistryFriendlyByteBuf): PokeNavSpawnDataPacket {
            val buckets = buffer.readList { it.readString() }
            val selectedBucket = buffer.readString()
            val entries = buffer.readList { buf ->
                SpawnChanceEntry(
                    speciesId = buf.readIdentifier(),
                    chance = buf.readFloat()
                )
            }

            return PokeNavSpawnDataPacket(
                buckets = buckets,
                selectedBucket = selectedBucket,
                entries = entries
            )
        }
    }

    data class SpawnChanceEntry(
        val speciesId: ResourceLocation,
        val chance: Float
    )
}
