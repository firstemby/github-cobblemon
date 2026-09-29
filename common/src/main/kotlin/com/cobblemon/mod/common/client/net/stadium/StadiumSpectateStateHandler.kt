/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.net.stadium

import com.cobblemon.mod.common.api.net.ClientNetworkPacketHandler
import com.cobblemon.mod.common.client.gui.stadium.StadiumSpectatorScreen
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.keybind.keybinds.StadiumSpectateBinding
import com.cobblemon.mod.common.net.messages.client.stadium.StadiumSpectateStatePacket
import com.cobblemon.mod.common.net.messages.server.stadium.StadiumSpectatePacket
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import java.util.UUID

object StadiumSpectateStateHandler : ClientNetworkPacketHandler<StadiumSpectateStatePacket> {
    private data class PendingRequest(
        val sessionId: UUID,
        val playerId: UUID,
        val connection: Any,
        val level: Any,
        val sentAt: Long
    ) {
        fun belongsTo(client: Minecraft) = client.player?.uuid == playerId && client.connection === connection && client.level === level
    }

    private const val PENDING_TIMEOUT_NANOS = 15_000_000_000L

    var state = StadiumSpectateStatePacket(null)
        private set
    private var pending: PendingRequest? = null
    val watching get() = state.watching && state.sessionId != null

    override fun handle(packet: StadiumSpectateStatePacket, client: Minecraft) {
        val wasWatching = watching
        state = packet
        pending?.takeIf { it.belongsTo(client) && (packet.sessionId == it.sessionId || packet.sessionId == null) }?.let { pending = null }
        if (watching && !wasWatching) client.setScreen(StadiumSpectatorScreen())
        if (!watching && wasWatching && (client.screen is StadiumSpectatorScreen ||
                client.screen is BattleGUI && CobblemonClient.battle?.spectating == true)) client.setScreen(null)
    }

    fun reset() { state = StadiumSpectateStatePacket(null); pending = null }

    fun toggle(now: Long = System.nanoTime()) {
        val id = state.sessionId ?: return
        if (pending != null) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return
        val connection = client.connection ?: return
        val level = client.level ?: return
        val request = PendingRequest(id, player.uuid, connection, level, now)
        pending = request
        try {
            StadiumSpectatePacket(id, watching).sendToServer()
        } catch (exception: Exception) {
            if (pending === request) pending = null
        }
    }

    fun tick(mc: Minecraft = Minecraft.getInstance(), now: Long = System.nanoTime()) {
        pending?.takeIf { !it.belongsTo(mc) || now - it.sentAt >= PENDING_TIMEOUT_NANOS }?.let { pending = null }
        if (watching && mc.player?.isAlive == true && mc.screen == null) mc.setScreen(StadiumSpectatorScreen())
    }

    fun render(context: GuiGraphics) {
        val mc = Minecraft.getInstance()
        if (state.sessionId == null || mc.player == null || (!watching && mc.screen != null)) return
        val key = StadiumSpectateBinding.translatedKeyMessage
        val text = Component.translatable(if (watching) "cobblemon.stadium.spectate.leave_hint" else "cobblemon.stadium.spectate.join_hint", key, state.name)
        val message = Component.literal(mc.font.plainSubstrByWidth(text.string, (mc.window.guiScaledWidth - 16).coerceAtLeast(0)))
        val textWidth = mc.font.width(message)
        val x = (mc.window.guiScaledWidth - textWidth) / 2
        val y = if (watching) 12 else mc.window.guiScaledHeight / 2 + 24
        context.fill(x - 8, y - 5, x + textWidth + 8, y + 14, 0xCC10252C.toInt())
        context.drawString(mc.font, message, x, y, 0xBDF9DD)
    }
}
