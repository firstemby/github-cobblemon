/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.net.IntSize
import com.cobblemon.mod.common.net.serverhandling.storage.SendOutPokemonHandler
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.readSizedInt
import com.cobblemon.mod.common.util.writeSizedInt
import net.minecraft.network.RegistryFriendlyByteBuf

class SendOutPokeballPacket() : NetworkPacket<SendOutPokeballPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
    }

    companion object {
        val ID = cobblemonResource("send_out_pokeball")
        fun decode(buffer: RegistryFriendlyByteBuf) = SendOutPokeballPacket()
    }
}