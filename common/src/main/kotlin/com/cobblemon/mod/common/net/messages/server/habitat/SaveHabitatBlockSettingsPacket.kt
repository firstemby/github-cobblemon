/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.server.habitat

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.habitats.dto.HabitatSettingsDTO
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Sent from the client to the server with the new settings to apply to the habitat block at that position.
 *
 * @author Hiroku
 * @since February 21st, 2026
 */
class SaveHabitatBlockSettingsPacket(
    val blockPos: BlockPos,
    val settings: HabitatSettingsDTO
) : NetworkPacket<SaveHabitatBlockSettingsPacket> {
    companion object {
        val ID = cobblemonResource("save_habitat_block_settings")
        fun decode(buffer: RegistryFriendlyByteBuf): SaveHabitatBlockSettingsPacket {
            val blockPos = buffer.readBlockPos()
            val settings = HabitatSettingsDTO()
            settings.decode(
                buffer = buffer,
                buckets = Cobblemon.bestSpawner.config.buckets
            )
            return SaveHabitatBlockSettingsPacket(blockPos, settings)
        }
    }

    override val id = ID
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeBlockPos(blockPos)
        settings.encode(buffer)
    }
}