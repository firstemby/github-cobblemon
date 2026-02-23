/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.habitat

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.habitat.HabitatEditGUI
import com.cobblemon.mod.common.net.messages.client.habitat.OpenHabitatBlockEditorPacket
import net.minecraft.client.Minecraft

object OpenHabitatBlockEditorHandler : ClientNetworkPacketHandler<OpenHabitatBlockEditorPacket> {
    override fun handle(packet: OpenHabitatBlockEditorPacket, client: Minecraft) {
        client.setScreen(
            HabitatEditGUI(
                blockPos = packet.blockPos,
                currentlySpawned = packet.currentlySpawned,
                currentPhase = packet.currentPhase,
                maxPokemonLevel = packet.maxPokemonLevel,
                spawnablePositionTypes = packet.spawnablePositionTypes,
                buckets = packet.buckets,
                activatedHabitatPools = packet.activatedHabitatPools,
                naturalHabitatPools = packet.naturalHabitatPools,
                habitatSettingsDTO = packet.habitatSettingsDTO
            )
        )
    }
}