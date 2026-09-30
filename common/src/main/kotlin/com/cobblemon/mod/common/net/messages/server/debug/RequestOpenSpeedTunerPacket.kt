/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.debug

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/** Asks the server to open the speed tuner, which checks debug keys and permission first. */
class RequestOpenSpeedTunerPacket : NetworkPacket<RequestOpenSpeedTunerPacket> {
    override val id = ID

    companion object {
        val ID = cobblemonResource("request_open_speed_tuner")

        fun decode(buffer: RegistryFriendlyByteBuf): RequestOpenSpeedTunerPacket {
            return RequestOpenSpeedTunerPacket()
        }
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {}
}
