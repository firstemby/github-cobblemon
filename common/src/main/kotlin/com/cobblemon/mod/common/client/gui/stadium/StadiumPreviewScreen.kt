/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.battles.stadium.StadiumBattleSession
import com.cobblemon.mod.common.client.gui.drawProfilePokemon
import com.cobblemon.mod.common.client.gui.stadium.widget.StadiumButton
import com.cobblemon.mod.common.client.net.battle.StadiumBattleStateHandler
import com.cobblemon.mod.common.client.render.models.blockbench.FloatingState
import com.cobblemon.mod.common.client.gui.ProfileTransformType
import com.cobblemon.mod.common.util.math.fromEulerXYZDegrees
import com.cobblemon.mod.common.net.messages.client.battle.StadiumBattleStatePacket
import com.cobblemon.mod.common.net.messages.client.battle.StadiumPreviewMember
import com.cobblemon.mod.common.net.messages.server.battle.StadiumPreviewChoicePacket
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f

class StadiumPreviewScreen(private var state: StadiumBattleStatePacket) : Screen(Component.translatable("cobblemon.stadium.preview.stadium_team_preview")) {
    val battleId get() = state.battleId
    private val order = mutableListOf<Int>()
    private var sent = false
    private val poses = mutableMapOf<Pair<String, Set<String>>, FloatingState>()
    private var geometry = StadiumScreenLayout.fit(320, 240)
    private val left get() = geometry.x + 16
    private val usableWidth get() = geometry.width - 32
    private val cellWidth get() = usableWidth / 6
    private val rowHeight get() = ((geometry.height - 142) / 2).coerceAtLeast(24)
    private val ownY get() = geometry.y + 67
    private val opponentY get() = ownY + rowHeight + 17

    fun update(next: StadiumBattleStatePacket) {
        if (next.rqid != state.rqid) { order.clear(); poses.clear(); sent = false }
        if (next.message.string.isNotEmpty()) sent = false
        state = next
        rebuild()
    }

    override fun init() {
        geometry = StadiumScreenLayout.fit(width, height)
        rebuild()
    }

    private fun rebuild() {
        clearWidgets()
        state.own.forEachIndexed { index, _ ->
            addRenderableWidget(object : StadiumButton(left + index * cellWidth, ownY, cellWidth - 3, rowHeight,
                Component.empty(), index + 1 in order, {
                if (!order.remove(index + 1)) order.add(index + 1)
                rebuild()
            }) {
                override fun createNarrationMessage() = Component.translatable("gui.narrate.button", state.own[index].name)
            }.also { it.active = !sent && state.phase == "preview" })
        }
        val footerY = geometry.y + geometry.height - 32
        addRenderableWidget(StadiumButton(left, footerY, 66, 20, Component.translatable("cobblemon.stadium.preview.reset")) {
            order.clear(); rebuild()
        }.also { it.active = !sent && state.phase == "preview" })
        addRenderableWidget(StadiumButton(left + usableWidth - 102, footerY, 102, 20,
            Component.literal(if (sent || state.phase == "waiting") I18n.get("cobblemon.stadium.preview.waiting") else I18n.get("cobblemon.stadium.preview.confirm_team")), true) {
            sent = true
            StadiumPreviewChoicePacket(battleId, state.rqid, order.toList()).sendToServer()
            rebuild()
        }.also { it.active = !sent && state.phase == "preview" && StadiumBattleSession.validOrder(order, state.own.map { member -> member.healthy }, state.slots) })
    }

    override fun renderBackground(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {}

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        graphics.fill(0, 0, width, height, 0xA0101D28.toInt())
        StadiumSkin.frame(graphics, geometry.x, geometry.y, geometry.width, geometry.height)
        graphics.drawString(font, I18n.get("cobblemon.stadium.preview.team_preview"), left, geometry.y + 19, StadiumSkin.INK, false)
        graphics.drawString(font, font.plainSubstrByWidth(StadiumBattleStateHandler.label(state), usableWidth), left, geometry.y + 34, StadiumSkin.MUTED, false)
        graphics.drawString(font, I18n.get("cobblemon.stadium.preview.choose_leads", state.slots), left, ownY - 13, StadiumSkin.INK, false)
        graphics.drawString(font, I18n.get("cobblemon.stadium.preview.opponent"), left, opponentY - 12, StadiumSkin.MUTED, false)
        super.render(graphics, mouseX, mouseY, partialTick)
        var hovered: Component? = null
        listOf(state.own to ownY, state.opponent to opponentY).forEachIndexed { side, (members, y) ->
            members.forEachIndexed { index, member ->
                val x = left + index * cellWidth
                if (side == 1) StadiumSkin.panel(graphics, StadiumSkin.Surface.DISPLAY, x, y, cellWidth - 3, rowHeight)
                val modelSize = minOf(cellWidth - 8, rowHeight - 12).coerceAtLeast(1)
                graphics.enableScissor(x + 1, y + 1, x + cellWidth - 4, y + rowHeight - 1)
                try {
                    drawMember(graphics, member, x + (cellWidth - 3) / 2,
                        y + (rowHeight - modelSize) / 2 - modelSize * 12 / 68, modelSize * 40f / 68f, partialTick)
                } finally {
                    graphics.disableScissor()
                }
                graphics.pose().pushPose()
                try {
                    graphics.pose().translate(0.0, 0.0, 1500.0)
                    val rank = order.indexOf(index + 1)
                    if (side == 0 && rank >= 0) graphics.drawString(font, "${rank + 1}${if (rank < state.slots) " ★" else ""}", x + 3, y + 3, StadiumSkin.INK, false)
                    if (side == 0 && !member.healthy) graphics.drawString(font, I18n.get("cobblemon.stadium.preview.fainted"), x + 2, y + rowHeight - 11, StadiumSkin.FOCUS, false)
                } finally {
                    graphics.pose().popPose()
                }
                if (mouseX in x until x + cellWidth - 3 && mouseY in y until y + rowHeight) hovered = member.name
            }
        }
        if (state.message.string.isNotEmpty()) graphics.drawString(font, font.plainSubstrByWidth(state.message.string, usableWidth), left, geometry.y + geometry.height - 44, StadiumSkin.FOCUS, false)
        hovered?.let {
            graphics.pose().pushPose()
            try {
                graphics.pose().translate(0.0, 0.0, 2000.0)
                graphics.renderTooltip(font, it, mouseX, mouseY)
            } finally {
                graphics.pose().popPose()
            }
        }
    }

    private fun drawMember(graphics: GuiGraphics, member: StadiumPreviewMember, x: Int, y: Int, scale: Float, partialTick: Float) {
        val species = ResourceLocation.tryParse(member.species)?.let(PokemonSpecies::getByIdentifier) ?: return
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(x.toDouble(), y.toDouble(), 1000.0)
            drawProfilePokemon(RenderablePokemon(species, member.aspects), graphics.pose(),
                Quaternionf().fromEulerXYZDegrees(Vector3f(13f, 35f, 0f)), state = poses.getOrPut(member.species to member.aspects) { FloatingState() },
                partialTicks = partialTick, scale = scale, profileTransformType = ProfileTransformType.POKEDEX)
        } finally { graphics.pose().popPose() }
    }

    override fun isPauseScreen() = false
    override fun shouldCloseOnEsc() = false
}
