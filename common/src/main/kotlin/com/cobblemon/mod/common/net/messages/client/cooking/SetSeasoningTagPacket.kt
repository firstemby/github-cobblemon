/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.cooking

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item

class SetSeasoningTagPacket(val seasoningTag: TagKey<Item>?) : NetworkPacket<SetSeasoningTagPacket> {
    companion object {
        val ID = cobblemonResource("set_seasoning_tag")
        fun decode(buffer: RegistryFriendlyByteBuf): SetSeasoningTagPacket {
            return SetSeasoningTagPacket(buffer.readNullable { TagKey.streamCodec(Registries.ITEM).decode(it) })
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(seasoningTag) { buf, value ->
            TagKey.streamCodec(Registries.ITEM).encode(buf, value)
        }
    }
}