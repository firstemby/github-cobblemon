/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.battle.ClientBattleInformationRepository
import com.cobblemon.mod.common.net.messages.client.battle.BattleActorInformationPacket
import net.minecraft.client.Minecraft

object BattleActorInformationHandler : ClientNetworkPacketHandler<BattleActorInformationPacket> {
    override fun handle(packet: BattleActorInformationPacket, client: Minecraft) {
        val pokemon = ClientBattleInformationRepository.actors[packet.actor]
        if (pokemon == null) return
        val previous = pokemon.find { it.uuid == packet.update.uuid }
        if (previous == null) return
        pokemon.add(pokemon.indexOf(previous), packet.update)
        pokemon.remove(previous)
    }
}