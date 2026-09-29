/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.stadium

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.stadium.StadiumItemCatalog
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

class OpenStadiumConfigPacket(
    val blockPos: BlockPos,
    val data: CompoundTag,
    val initialTab: Int = 0,
    val initialSubTab: Int = 0,
    val initialLayout: String = "singles",
    val heldItems: Map<ResourceLocation, String> = StadiumItemCatalog.collect()
) : NetworkPacket<OpenStadiumConfigPacket> {
    companion object {
        val ID = cobblemonResource("open_stadium_config")
        const val MAX_HELD_ITEMS = 65_536
        private const val MIN_HELD_ITEM_BYTES = 2

        fun decode(buffer: RegistryFriendlyByteBuf): OpenStadiumConfigPacket {
            val blockPos = buffer.readBlockPos()
            val data = buffer.readNbt() ?: CompoundTag()
            val initialTab = buffer.readVarInt()
            val initialSubTab = buffer.readVarInt()
            val initialLayout = buffer.readUtf(16)
            val heldItemCount = buffer.readVarInt()
            val maxHeldItems = minOf(MAX_HELD_ITEMS, buffer.readableBytes() / MIN_HELD_ITEM_BYTES)
            require(heldItemCount in 0..maxHeldItems) { "Invalid held item count: $heldItemCount" }
            val heldItems = LinkedHashMap<ResourceLocation, String>(heldItemCount)
            repeat(heldItemCount) {
                heldItems[buffer.readResourceLocation()] = buffer.readUtf(80)
            }
            return OpenStadiumConfigPacket(blockPos, data, initialTab, initialSubTab, initialLayout, heldItems)
        }
    }

    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        require(heldItems.size <= MAX_HELD_ITEMS) { "Too many held items: ${heldItems.size}" }
        buffer.writeBlockPos(blockPos)
        buffer.writeNbt(data)
        buffer.writeVarInt(initialTab)
        buffer.writeVarInt(initialSubTab)
        buffer.writeUtf(initialLayout, 16)
        buffer.writeMap(heldItems, { buf, item -> buf.writeResourceLocation(item) }, { buf, effect -> buf.writeUtf(effect, 80) })
    }
}
