/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.molangui

import com.cobblemon.mod.common.api.molangui.MolangUiRegistry
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.client.molangui.MolangUiCompatibilityRequestPacket
import com.cobblemon.mod.common.net.messages.server.molangui.MolangUiCompatibilityResponsePacket
import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation

object MolangUiCompatibilityRequestHandler : ClientNetworkPacketHandler<MolangUiCompatibilityRequestPacket> {
    private var requested: Set<ResourceLocation> = emptySet()
    override fun handle(packet: MolangUiCompatibilityRequestPacket, client: Minecraft) {
        requested = packet.contracts.keys.toSet()
        respond()
    }
    fun respond() {
        if (requested.isEmpty()) return
        val available = MolangUiRegistry.getVersionMap().filterKeys { it in requested }
        MolangUiCompatibilityResponsePacket(available).sendToServer()
    }
    fun reset() { requested = emptySet() }
}
