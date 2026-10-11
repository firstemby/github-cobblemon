/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.debug

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.api.permission.CobblemonPermissions.USE_SPEED_TUNER
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.net.messages.client.debug.OpenSpeedTunerPacket
import com.cobblemon.mod.common.net.messages.client.debug.SpeedTunerEntry
import com.cobblemon.mod.common.net.messages.server.debug.RequestOpenSpeedTunerPacket
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolveFloat
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object RequestOpenSpeedTunerHandler : ServerNetworkPacketHandler<RequestOpenSpeedTunerPacket> {
    override fun handle(
        packet: RequestOpenSpeedTunerPacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        if (!Cobblemon.config.enableDebugKeys) {
            player.sendSystemMessage(lang("requires_debug_keys").red())
            return
        }

        if (!Cobblemon.permissionValidator.hasPermission(player, USE_SPEED_TUNER)) {
            player.sendSystemMessage(lang("requires_permission").red())
            return
        }

        val entries = PokemonSpecies.implemented.map { species ->
            val walk = species.standardForm.behaviour.moving.walk
            SpeedTunerEntry(
                speciesId = species.resourceIdentifier,
                dex = species.nationalPokedexNumber,
                // Speeds that are expressions needing a pokemon context can't be resolved here, show them as 0
                walkSpeed = runCatching { mainThreadRuntime.resolveFloat(walk.walkSpeed) }.getOrDefault(0F),
                sprintSpeed = walk.sprintSpeed?.let { runCatching { mainThreadRuntime.resolveFloat(it) }.getOrDefault(0F) }
            )
        }
        OpenSpeedTunerPacket(entries).sendToPlayer(player)
    }
}
