/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.serverhandling.battle

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork.sendPacket
import com.cobblemon.mod.common.api.net.ServerNetworkPacketHandler
import com.cobblemon.mod.common.battles.BattleRegistry
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import com.cobblemon.mod.common.net.messages.client.battle.BattleMusicPacket
import com.cobblemon.mod.common.net.messages.server.battle.SpectateNearestBattlePacket
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import org.apache.logging.log4j.LogManager

object SpectateNearestBattleHandler : ServerNetworkPacketHandler<SpectateNearestBattlePacket> {
    val LOGGER = LogManager.getLogger()
    override fun handle(
        packet: SpectateNearestBattlePacket,
        server: MinecraftServer,
        player: ServerPlayer
    ) {
        if (BattleRegistry.getBattleBySpectatorId(player.uuid) != null) return
        if (BattleRegistry.getBattleByParticipatingPlayer(player) != null) return

        val nearest = BattleRegistry.getPlayersCurrentlyBattling()
            .filter { it != player && it.serverLevel() == player.serverLevel() && it.distanceTo(player) <= 64 }
            .minByOrNull { it.distanceTo(player) }

        if (nearest == null) return

        val battle = BattleRegistry.getBattleByParticipatingPlayerId(nearest.uuid)
        if (battle != null && Cobblemon.config.allowSpectating) {
            val target = battle.actors.filterIsInstance<PlayerBattleActor>().firstOrNull { it.uuid == nearest.uuid }
            battle.startSpectating(player)
            target?.battleTheme?.let { player.sendPacket(BattleMusicPacket(it)) }
        }
        else {
            LOGGER.error("Battle of player id ${nearest.uuid} not found (${player.uuid} tried spectating)")
        }
    }

}