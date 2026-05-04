/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.battle

import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import java.time.Instant

class BattleTimerPacket(val mustChooseBy: Instant?) : NetworkPacket<BattleTimerPacket> {
    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeNullable(mustChooseBy) { _, value -> buffer.writeInstant(value) }
    }

    companion object {
        val ID = cobblemonResource("battle_timer")
        fun decode(buffer: RegistryFriendlyByteBuf) = BattleTimerPacket(
            mustChooseBy = buffer.readNullable { buffer.readInstant() }
        )
    }
}