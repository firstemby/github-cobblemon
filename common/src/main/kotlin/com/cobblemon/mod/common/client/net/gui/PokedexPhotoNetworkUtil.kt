/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.gui

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.net.messages.client.ui.PokedexPhotoGalleryPacket
import com.cobblemon.mod.common.pokedex.photo.PokedexPhotoRepository
import net.minecraft.server.level.ServerPlayer
import kotlin.math.max

object PokedexPhotoNetworkUtil {
    fun sendGallery(player: ServerPlayer) {
        val photos = PokedexPhotoRepository.listFor(player.uuid)
        val maxAllowed = max(1, Cobblemon.config.pokedexScannerPhotoLimitPerPlayer)
        PokedexPhotoGalleryPacket(maxAllowed, photos).sendToPlayer(player)
    }
}

