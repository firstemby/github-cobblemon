/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.tms.obtain

import com.cobblemon.mod.common.api.tms.ObtainMethod
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

/**
 * An [ObtainMethod] that triggers when you get the move on a Pokemon you own or insert into a Data Monitor
 * @author Plastered_Crab
 */
class UnlockableObtainMethod : ObtainMethod {
    override val passive = false

    companion object {
        val ID = cobblemonResource("unlockable")

        fun readFromBuffer(buffer: RegistryFriendlyByteBuf): UnlockableObtainMethod {
            return UnlockableObtainMethod()
        }
    }

    override fun matches(player: ServerPlayer) = false

    override fun writeToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf("cobblemon:unlockable")
    }
}
