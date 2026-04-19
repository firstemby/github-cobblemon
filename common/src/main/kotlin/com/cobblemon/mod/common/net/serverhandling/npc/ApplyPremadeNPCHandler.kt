/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.npc

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.npc.premades.PremadeNPCs
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.server.npc.ApplyPremadeNPCPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ApplyPremadeNPCHandler : ServerNetworkPacketHandler<ApplyPremadeNPCPacket> {
    override fun handle(packet: ApplyPremadeNPCPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.permissionValidator.hasPermission(player, CobblemonPermissions.NPC_EDIT)) {
            return
        }
        val npc = player.serverLevel().getEntity(packet.npcId) as? NPCEntity ?: return
        val premade = PremadeNPCs.premadeNPCsById[packet.premadeNPCId] ?: return
        premade.applyTo(npc)
        player.closeContainer()
    }
}