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
 * An [ObtainMethod] that checks the player's Y level.
 *
 * @param yLevel The Y level to check for.
 * @param operator The operator to use. Can be "equals", "less", or "greater".
 * @author whatsy
 */
class PlayerYObtainMethod(val yLevel: Int, val operator: String) : ObtainMethod {
    override val passive = true

    companion object {
        val ID = cobblemonResource("y_level")

        fun readFromBuffer(buffer: RegistryFriendlyByteBuf): PlayerYObtainMethod {
            val yLevel = buffer.readVarInt()
            val operator = buffer.readUtf()
            return PlayerYObtainMethod(yLevel, operator)
        }
    }

    override fun matches(player: ServerPlayer) = when (operator) {
        "equals" -> player.y.toInt() == yLevel
        "less" -> player.y.toInt() < yLevel
        "greater" -> player.y.toInt() > yLevel
        else -> false
    }

    override fun writeToBuffer(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf("cobblemon:y_level")
        buffer.writeVarInt(yLevel)
        buffer.writeUtf(operator)
    }
}
