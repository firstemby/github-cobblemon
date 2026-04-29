/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.pokenav

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

class RequestPokeNavAreaScanPacket : NetworkPacket<RequestPokeNavAreaScanPacket> {
    override val id = ID

    override fun encode(buffer: RegistryFriendlyByteBuf) = Unit

    companion object {
        val ID = cobblemonResource("request_pokenav_area_scan")

        fun decode(buffer: RegistryFriendlyByteBuf) = RequestPokeNavAreaScanPacket()
    }
}
