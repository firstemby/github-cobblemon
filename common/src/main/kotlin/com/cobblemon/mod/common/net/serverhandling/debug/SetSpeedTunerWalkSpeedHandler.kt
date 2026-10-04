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
import com.cobblemon.mod.common.net.messages.server.debug.SetSpeedTunerWalkSpeedPacket
import com.cobblemon.mod.common.util.asExpression
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

/**
 * Changes the loaded species data so every pokemon of that species walks at the new speed straight away.
 * Only lasts until the species data is reloaded, the tuner export is what makes it permanent.
 */
object SetSpeedTunerWalkSpeedHandler : ServerNetworkPacketHandler<SetSpeedTunerWalkSpeedPacket> {
    override fun handle(packet: SetSpeedTunerWalkSpeedPacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return
        if (!Cobblemon.permissionValidator.hasPermission(player, USE_SPEED_TUNER)) return
        val species = PokemonSpecies.getByIdentifier(packet.speciesId) ?: return
        species.standardForm.behaviour.moving.walk.walkSpeed = packet.walkSpeed.toString().asExpression()
    }
}
