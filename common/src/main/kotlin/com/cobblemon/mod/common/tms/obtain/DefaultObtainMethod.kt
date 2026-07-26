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
 * An [ObtainMethod] that unlocks immediately by default.
 * Used for TMs that aren't unlocked and are available immediately.
 * TMs with this [ObtainMethod] will not display an unlock text.
 *
 * @author Plastered_Crab
 */
class DefaultObtainMethod : ObtainMethod {
    override val passive = true

    companion object {
        val ID = cobblemonResource("default")

        fun readFromBuffer(buffer: RegistryFriendlyByteBuf): DefaultObtainMethod {
            return DefaultObtainMethod()
        }
    }

    override fun matches(player: ServerPlayer) = true

    override fun writeToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf("cobblemon:default")
    }
}

