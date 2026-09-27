/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.bag

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class UpdateBagMenuDataPacket(val categoryId: Int, val scroll: Int): NetworkPacket<UpdateBagMenuDataPacket> {
    companion object {
        val ID = cobblemonResource("update_bag_menu_data")
        fun decode(buffer: RegistryFriendlyByteBuf): UpdateBagMenuDataPacket {
            return UpdateBagMenuDataPacket(buffer.readInt(), buffer.readInt())
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeInt(categoryId)
        buffer.writeInt(scroll)
    }
}