/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling

import com.cobblemon.mod.common.CobblemonPokeSkills
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.net.messages.server.UsePokeSkillPacket
import com.cobblemon.mod.common.util.party
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

object UsePokeSkillHandler : ServerNetworkPacketHandler<UsePokeSkillPacket> {
    override fun handle(packet: UsePokeSkillPacket, server: MinecraftServer, player: ServerPlayer) {
        val party = player.party()
        val pokemon = party[packet.pokemonId] ?: return // Who?
        val skill = CobblemonPokeSkills.getAccessibleSkills(player, pokemon).firstOrNull { it.id == packet.skillId } ?: return // What skill?
        if (skill.canUse(player, pokemon)) {
            skill.use(player, pokemon)
        }
    }
}