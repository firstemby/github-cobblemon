/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.summary.widgets.screens.moves

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.moves.Move
import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.text.text
import com.cobblemon.mod.common.api.types.ElementalTypes
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.MoveCategoryIcon
import com.cobblemon.mod.common.client.gui.TypeIcon
import com.cobblemon.mod.common.client.gui.summary.Summary
import com.cobblemon.mod.common.client.gui.summary.widgets.common.SummaryScrollList
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponent
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.BenchMovePacket
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import com.cobblemon.mod.common.util.math.toRGB
import net.minecraft.client.gui.GuiGraphics

class MoveSwapScreen(
    x: Int,
    y: Int,
    val movesWidget: MovesWidget,
    var replacedMove: Move?
): SummaryScrollList<MoveSwapScreen.MoveSlot>(
    x,
    y,
    lang("ui.moves.switch"),
    SLOT_HEIGHT + SLOT_SPACING
) {
    companion object {
        const val SLOT_HEIGHT = 18
        const val SLOT_SPACING = 3

        private val moveResource = cobblemonResource("textures/gui/summary/summary_move_condensed.png")
        private val moveOverlayResource = cobblemonResource("textures/gui/summary/summary_move_overlay_condensed.png")
        private val forgetMoveOverlayResource = cobblemonResource("textures/gui/summary/summary_move_condensed_empty.png")
        private val forgetMoveIconResource = cobblemonResource("textures/gui/summary/summary_move_condensed_empty_icon.png")
    }

    var controllerComponentManager = ControllerComponentManager(movesWidget.summary).also {
        it.onActiveChange = { state ->
            if (state) it.focused?.onSelect?.invoke() else it.focused?.onUnselect?.invoke()
        }
    }

    public override fun addEntry(entry: MoveSlot): Int {
        val i = super.addEntry(entry)
        controllerComponentManager.componentList["moves_${i}"] = ControllerComponent().also {
            it.associatedGUIElement = entry
            it.onPress = {
                entry.onPress()
                movesWidget.summary.controllerComponentManager.let { controller -> controller.focused = controller.componentList["center_screen"] }
            }
            it.onSelect = {
                ensureVisible(entry)
                entry.slotSelected = true
            }
            it.onUnselect = {
                entry.slotSelected = false
            }
        }
        if (children().size > 1) {
            controllerComponentManager.componentList["moves_${i}"]?.let {
                it.pairUp(controllerComponentManager.componentList["moves_${i-1}"])
                it.pairDown(controllerComponentManager.componentList["moves_${0}"])
            }
        } else {
            controllerComponentManager.focused = controllerComponentManager.componentList["moves_${i}"]
        }
        return i
    }

    class MoveSlot(val pane: MoveSwapScreen, val move: MoveTemplate?, val ppRaisedStages: Int, pokemon: Pokemon) : Entry<MoveSlot>() {
        override fun getNarration() = move?.displayName ?: lang("ui.moves.forget")

        val elementalType = move?.getEffectiveElementalType(pokemon) ?: ElementalTypes.NORMAL
        var slotSelected = false

        override fun render(
            context: GuiGraphics,
            index: Int,
            rowTop: Int,
            rowLeft: Int,
            rowWidth: Int,
            rowHeight: Int,
            mouseX: Int,
            mouseY: Int,
            isHovered: Boolean,
            partialTicks: Float
        ) {
            val matrices = context.pose()
            val tweakedRowTop = rowTop - (SLOT_SPACING / 2) + 1
            if (move != null) {
                val rgb = elementalType.hue.toRGB()

                blitk(
                        matrixStack = matrices,
                        texture = moveResource,
                        x = rowLeft,
                        y = tweakedRowTop,
                        height = SLOT_HEIGHT,
                        width = rowWidth,
                        vOffset = if (isHovered || slotSelected) SLOT_HEIGHT else 0,
                        textureHeight = SLOT_HEIGHT * 2,
                        red = rgb.first,
                        green = rgb.second,
                        blue = rgb.third
                )

                blitk(
                        matrixStack = matrices,
                        texture = moveOverlayResource,
                        x = rowLeft,
                        y = tweakedRowTop,
                        height = SLOT_HEIGHT,
                        width = rowWidth
                )

                // Type Icon
                TypeIcon(
                    x = rowLeft - 9,
                    y = tweakedRowTop,
                    type = elementalType
                ).render(context)

                // Move Category
                MoveCategoryIcon(
                        x = rowLeft + 77,
                        y = tweakedRowTop + 1.5,
                        category = move.damageCategory
                ).render(context)

                drawScaledText(
                        context = context,
                        text = move.displayName,
                        x = rowLeft + 14,
                        y = tweakedRowTop + 3.5,
                        scale = MovesWidget.SCALE,
                        shadow = true
                )

                // Move icons
                blitk(
                        matrixStack = matrices,
                        texture = MovesWidget.movesPowerIconResource,
                        x= (rowLeft + 10) / MovesWidget.SCALE,
                        y = (tweakedRowTop + 11) / MovesWidget.SCALE,
                        width = MovesWidget.MOVE_ICON_SIZE,
                        height = MovesWidget.MOVE_ICON_SIZE,
                        scale = MovesWidget.SCALE
                )

                blitk(
                        matrixStack = matrices,
                        texture = MovesWidget.movesAccuracyIconResource,
                        x= (rowLeft + 30) / MovesWidget.SCALE,
                        y = (tweakedRowTop + 11) / MovesWidget.SCALE,
                        width = MovesWidget.MOVE_ICON_SIZE,
                        height = MovesWidget.MOVE_ICON_SIZE,
                        scale = MovesWidget.SCALE
                )

                blitk(
                        matrixStack = matrices,
                        texture = MovesWidget.movesEffectIconResource,
                        x= (rowLeft + 53.5) / MovesWidget.SCALE,
                        y = (tweakedRowTop + 11) / MovesWidget.SCALE,
                        width = MovesWidget.MOVE_ICON_SIZE,
                        height = MovesWidget.MOVE_ICON_SIZE,
                        scale = MovesWidget.SCALE
                )

                val movePower = if (move.power.toInt() > 0) move.power.toInt().toString().text() else "—".text()
                drawScaledText(
                        context = context,
                        text = movePower,
                        x = rowLeft + 16.5,
                        y = tweakedRowTop + 12,
                        scale = MovesWidget.SCALE,
                        shadow = true
                )

                drawScaledText(
                        context = context,
                        text = pane.movesWidget.format(move.accuracy).text(),
                        x = rowLeft + 37,
                        y = tweakedRowTop + 12,
                        scale = MovesWidget.SCALE,
                        shadow = true
                )

                drawScaledText(
                        context = context,
                        text = pane.movesWidget.format(move.effectChances.firstOrNull() ?: 0.0).text(),
                        x = rowLeft + 60.5,
                        y = tweakedRowTop + 12,
                        scale = MovesWidget.SCALE,
                        shadow = true
                )

                val pp = move.pp + ppRaisedStages * move.pp / 5
                drawScaledText(
                        context = context,
                        text = lang("ui.moves.pp", pp),
                        x = rowLeft + 76,
                        y = tweakedRowTop + 12,
                        scale = MovesWidget.SCALE,
                        shadow = true
                )
            } else {
                // Forget move slot
                blitk(
                        matrixStack = matrices,
                        texture = forgetMoveOverlayResource,
                        x = rowLeft - 9,
                        y = tweakedRowTop,
                        height = SLOT_HEIGHT,
                        width = 100,
                        vOffset = if (isHovered || slotSelected) SLOT_HEIGHT else 0,
                        textureHeight = SLOT_HEIGHT * 2,
                )
                blitk(
                        matrixStack = matrices,
                        texture = forgetMoveIconResource,
                        x = rowLeft + rowWidth / 2 - 7,
                        y = tweakedRowTop + SLOT_HEIGHT / 2 - 4,
                        height = 7,
                        width = 7,
                        vOffset = if (isHovered || slotSelected) 7 else 0,
                        textureHeight = 14,
                )
            }

        }

        override fun mouseClicked(d: Double, e: Double, i: Int): Boolean {
            if (isMouseOver(d, e)) {
                onPress()
                return true
            }
            return false
        }

        fun onPress() {
            switchMove()
            pane.movesWidget.summary.playSound(CobblemonSounds.GUI_CLICK)
            pane.movesWidget.summary.displaySideScreen(Summary.PARTY)
        }

        fun switchMove() {
            val pokemon = pane.movesWidget.summary.selectedPokemon
            val isParty = pokemon in CobblemonClient.storage.party
            CobblemonNetwork.sendToServer(
                BenchMovePacket(
                    isParty = isParty,
                    uuid = pokemon.uuid,
                    oldMove = pane.replacedMove?.template,
                    newMove = move
                )
            )
        }
    }
}