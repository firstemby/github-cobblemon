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
import com.cobblemon.mod.common.api.npc.premades.PremadeNPC
import com.cobblemon.mod.common.api.npc.premades.PremadeNPCs
import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.server.npc.SavePremadeFromNPCPacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object SavePremadeFromNPCHandler : ServerNetworkPacketHandler<SavePremadeFromNPCPacket> {
    override fun handle(packet: SavePremadeFromNPCPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.permissionValidator.hasPermission(player, CobblemonPermissions.NPC_EDIT)) {
            return
        }
        val npc = player.serverLevel().getEntity(packet.npcId) as? NPCEntity ?: return
        val premade = PremadeNPC().copyFrom(
            npc = npc,
            includeVariation = packet.includeVariation,
            includeSettings = packet.includeSettings,
            includeParty = packet.includeParty,
            includeClassification = packet.includeClassification
        ).also {
            it.id = packet.premadeNPCId
            it.displayName = packet.displayName
        }

        PremadeNPCs.savePremadeToWorld(premade)
    }
}