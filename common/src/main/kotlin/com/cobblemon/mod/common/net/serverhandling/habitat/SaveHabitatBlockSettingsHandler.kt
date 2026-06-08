/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.habitat

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.habitats.SaveHabitatBlockSettingsEvent
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.block.habitat.HabitatBlockEntity
import com.cobblemon.mod.common.net.messages.server.habitat.SaveHabitatBlockSettingsPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SaveHabitatBlockSettingsHandler : ServerNetworkPacketHandler<SaveHabitatBlockSettingsPacket> {
    override fun handle(packet: SaveHabitatBlockSettingsPacket, server: MinecraftServer, player: ServerPlayer) {
        val world = player.serverLevel()
        val blockEntity = world.getBlockEntity(packet.blockPos) as? HabitatBlockEntity ?: return // it's broken
        val pre = SaveHabitatBlockSettingsEvent.Pre(
            player = player,
            world = world,
            blockPos = packet.blockPos,
            habitatBlockEntity = blockEntity,
            settings = packet.settings
        )

        if (!player.isCreative) {
            // mf does not have permission
            return
        }

        CobblemonEvents.HABITAT_SETTINGS_SAVED_PRE.postThen(pre) {
            blockEntity.applySettings(habitatSettingsDTO = packet.settings)
            val post = SaveHabitatBlockSettingsEvent.Post(
                player = player,
                world = world,
                blockPos = packet.blockPos,
                habitatBlockEntity = blockEntity,
                settings = packet.settings
            )
            CobblemonEvents.HABITAT_SETTINGS_SAVED_POST.post(post)
        }
    }
}