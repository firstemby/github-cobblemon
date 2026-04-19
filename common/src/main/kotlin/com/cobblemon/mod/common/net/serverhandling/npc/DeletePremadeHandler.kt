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
import com.cobblemon.mod.common.net.messages.server.npc.DeletePremadePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object DeletePremadeHandler : ServerNetworkPacketHandler<DeletePremadePacket> {
    override fun handle(packet: DeletePremadePacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.permissionValidator.hasPermission(player, CobblemonPermissions.NPC_DELETE)) {
            return
        }
        PremadeNPCs.deletePremade(packet.premadeId)
    }
}