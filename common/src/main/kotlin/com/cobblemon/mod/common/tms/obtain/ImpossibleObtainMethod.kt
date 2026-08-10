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
 * An [ObtainMethod] for TMs that cannot be unlocked through gameplay triggers.
 * These TMs are intended to be granted manually by command or MoLang only.
 */
class ImpossibleObtainMethod : ObtainMethod {
    override val passive = false

    companion object {
        val ID = cobblemonResource("impossible")

        fun readFromBuffer(buffer: RegistryFriendlyByteBuf): ImpossibleObtainMethod {
            return ImpossibleObtainMethod()
        }
    }

    override fun matches(player: ServerPlayer) = false

    override fun writeToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf("cobblemon:impossible")
    }
}
