/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.util

import com.cobblemon.mod.common.net.messages.client.SyncShoulderEntityPacket
import com.cobblemon.mod.common.platform.events.PlatformEvents
import net.minecraft.server.level.ServerPlayer

object ShoulderEntitySynchronizer {
    internal fun register() {
        PlatformEvents.SERVER_PLAYER_LOGIN.subscribe { event ->
            val joiningPlayer = event.player
            val players = server()?.playerList?.players ?: return@subscribe

            players.forEach { trackedPlayer ->
                syncToPlayer(trackedPlayer, joiningPlayer)
                if (trackedPlayer.uuid != joiningPlayer.uuid) {
                    syncToPlayer(joiningPlayer, trackedPlayer)
                }
            }
        }
    }

    @JvmStatic
    fun sync(player: ServerPlayer) {
        createPacket(player).sendToAllPlayers()
    }

    private fun syncToPlayer(sourcePlayer: ServerPlayer, targetPlayer: ServerPlayer) {
        createPacket(sourcePlayer).sendToPlayer(targetPlayer)
    }

    private fun createPacket(player: ServerPlayer): SyncShoulderEntityPacket {
        return SyncShoulderEntityPacket(
            entityId = player.id,
            leftShoulder = player.getShoulderEntity(true).copy(),
            rightShoulder = player.getShoulderEntity(false).copy()
        )
    }
}
