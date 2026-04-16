/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.duck.PlayerDuck
import com.cobblemon.mod.common.net.messages.client.SyncShoulderEntityPacket
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

object SyncShoulderEntityHandler : ClientNetworkPacketHandler<SyncShoulderEntityPacket> {
    private val pendingShoulders = mutableMapOf<Int, Pair<CompoundTag, CompoundTag>>()

    override fun handle(packet: SyncShoulderEntityPacket, client: Minecraft) {
        val player = client.level?.getEntity(packet.entityId) as? Player
        if (player == null) {
            pendingShoulders[packet.entityId] = packet.leftShoulder.copy() to packet.rightShoulder.copy()
            return
        }

        apply(player, packet.leftShoulder, packet.rightShoulder)
    }

    fun tryApply(entity: Entity) {
        val player = entity as? Player ?: return
        val pending = pendingShoulders.remove(player.id) ?: return
        apply(player, pending.first, pending.second)
    }

    fun clear() {
        pendingShoulders.clear()
    }

    private fun apply(player: Player, leftShoulder: CompoundTag, rightShoulder: CompoundTag) {
        val duck = player as? PlayerDuck ?: return
        duck.setShoulderEntityLeft(leftShoulder)
        duck.setShoulderEntityRight(rightShoulder)
    }
}
