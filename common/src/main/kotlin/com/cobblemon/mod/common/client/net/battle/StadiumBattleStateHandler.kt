/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.battle

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.stadium.StadiumPreviewScreen
import com.cobblemon.mod.common.net.messages.client.battle.StadiumBattleStatePacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics

object StadiumBattleStateHandler : ClientNetworkPacketHandler<StadiumBattleStatePacket> {
    var state: StadiumBattleStatePacket? = null
        private set

    override fun handle(packet: StadiumBattleStatePacket, client: Minecraft) {
        if (packet.phase == "closed") {
            if (state?.battleId == packet.battleId) state = null
            if ((client.screen as? StadiumPreviewScreen)?.battleId == packet.battleId) client.setScreen(null)
            return
        }
        state = packet
        val screen = client.screen as? StadiumPreviewScreen
        if (packet.phase in setOf("preview", "waiting")) {
            if (screen?.battleId != packet.battleId) client.setScreen(StadiumPreviewScreen(packet))
            else screen.update(packet)
        } else if (screen?.battleId == packet.battleId) client.setScreen(null)
    }

    fun reset() {
        state = null
    }

    fun render(graphics: GuiGraphics) {
        val client = Minecraft.getInstance()
        if (client.level == null) { state = null; return }
        if (client.screen is StadiumPreviewScreen) return
        val value = state ?: return
        if (value.decisionSeconds < 0 && value.matchSeconds < 0) return
        val text = client.font.plainSubstrByWidth(label(value), (graphics.guiWidth() - 8).coerceAtLeast(0))
        val x = (graphics.guiWidth() - client.font.width(text)) / 2
        graphics.drawString(client.font, text, x.coerceAtLeast(4), 5, 0xFFFFFFFF.toInt())
    }

    fun label(value: StadiumBattleStatePacket) = buildList {
        if (value.decisionSeconds >= 0) add(I18n.get("cobblemon.stadium.clock.you_opponent", time(value.decisionSeconds), time(value.opponentSeconds)))
        if (value.matchSeconds >= 0) add(I18n.get("cobblemon.stadium.clock.match", time(value.matchSeconds)))
    }.joinToString("  |  ")

    private fun time(seconds: Int) = if (seconds < 0) "—" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
