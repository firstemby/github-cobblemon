/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.storage.pc

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.storage.WallpaperCollectionEvent
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.storage.pc.PCBoxWallpapersPacket
import com.cobblemon.mod.common.net.messages.client.storage.pc.wallpaper.SetPCBoxWallpapersPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object PCBoxWallpapersHandler : ServerNetworkPacketHandler<PCBoxWallpapersPacket> {
    override fun handle(packet: PCBoxWallpapersPacket, server: MinecraftServer, player: ServerPlayer) {
        CobblemonEvents.WALLPAPER_COLLECTION_EVENT.post(WallpaperCollectionEvent(player, ArrayList(packet.wallpapers)), then = { event ->
            Cobblemon.wallpapers = Cobblemon.wallpapers.plus(player.uuid to event.wallpapers)
            SetPCBoxWallpapersPacket(event.wallpapers).sendToPlayer(player)
        })
    }
}