/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.molangui

import com.cobblemon.mod.common.api.molangui.MolangUiManifestRegistry
import com.cobblemon.mod.common.api.molangui.MolangUiSessionManager
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiVersionPacket
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object MolangUiVersionHandler : ServerNetworkPacketHandler<MolangUiVersionPacket> {
    override fun handle(packet: MolangUiVersionPacket, server: MinecraftServer, player: ServerPlayer) {
        // For now, hardcode policy to "warn" (config integration is future work)
        val policy = "warn"

        val mismatches = mutableListOf<String>()
        for ((guiId, clientVersion) in packet.versions) {
            val expectedVersion = MolangUiManifestRegistry.getExpectedVersion(guiId)
            if (expectedVersion != null && expectedVersion != clientVersion) {
                mismatches.add("$guiId: client=$clientVersion, server=$expectedVersion")
            }
        }

        if (mismatches.isEmpty()) return

        when (policy) {
            "warn" -> {
                player.sendSystemMessage(Component.literal(
                    "[Molang UI] Version mismatch: ${mismatches.joinToString(", ")}"
                ))
            }
            "disable" -> {
                val mismatchedIds = packet.versions.keys
                    .filter { guiId ->
                        val expected = MolangUiManifestRegistry.getExpectedVersion(guiId)
                        expected != null && expected != packet.versions[guiId]
                    }
                    .map { it.toString() }
                    .toSet()
                MolangUiSessionManager.setDisabledGuis(player.uuid, mismatchedIds)
                player.sendSystemMessage(Component.literal(
                    "[Molang UI] Disabled mismatched GUIs: ${mismatchedIds.joinToString(", ")}"
                ))
            }
            "disconnect" -> {
                player.connection.disconnect(Component.literal(
                    "Molang UI version mismatch. Please update your resource pack."
                ))
            }
        }
    }
}
