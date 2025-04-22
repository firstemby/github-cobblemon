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
import com.cobblemon.mod.common.api.riding.stats.RidingStat
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity
import com.cobblemon.mod.common.net.messages.server.debug.ServerboundUpdateRidingStatRangePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object ServerboundUpdateRidingStatRangeHandler : ServerNetworkPacketHandler<ServerboundUpdateRidingStatRangePacket> {

    override fun handle(packet: ServerboundUpdateRidingStatRangePacket, server: MinecraftServer, player: ServerPlayer) {
        if (!Cobblemon.config.enableDebugKeys) return

        val entity = player.level().getEntity(packet.entity) ?: return
        if (entity !is PokemonEntity) return
        if (entity.controllingPassenger != player) return

        if (packet.minSpeed < packet.maxSpeed) {
            entity.rideProp.updateStatRange(RidingStat.SPEED, packet.ridingStyle, packet.minSpeed, packet.maxSpeed)
        }
        if (packet.minAcceleration < packet.maxAcceleration) {
            entity.rideProp.updateStatRange(RidingStat.ACCELERATION, packet.ridingStyle, packet.minAcceleration, packet.maxAcceleration)
        }
        if (packet.minSkill < packet.maxSkill) {
            entity.rideProp.updateStatRange(RidingStat.SKILL, packet.ridingStyle, packet.minSkill, packet.maxSkill)
        }
        if (packet.minJump < packet.maxJump) {
            entity.rideProp.updateStatRange(RidingStat.JUMP, packet.ridingStyle, packet.minJump, packet.maxJump)
        }
        if (packet.minStamina < packet.maxStamina) {
            entity.rideProp.updateStatRange(RidingStat.STAMINA, packet.ridingStyle, packet.minStamina, packet.maxStamina)
        }
    }

}
