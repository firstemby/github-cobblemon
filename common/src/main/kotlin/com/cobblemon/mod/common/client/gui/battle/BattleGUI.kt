/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.battle

import com.cobblemon.mod.common.battles.PassActionResponse
import com.cobblemon.mod.common.battles.ShowdownActionResponse
import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.battle.ClientBattleActor
import com.cobblemon.mod.common.client.battle.SingleActionRequest
import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleBackButton
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleGeneralActionSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleMoveSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleTargetSelection
import com.cobblemon.mod.common.client.gui.battle.subscreen.ForfeitConfirmationSelection
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleMessagePane
import com.cobblemon.mod.common.client.gui.battle.widgets.BattleOptionTile
import com.cobblemon.mod.common.client.gui.summary.Summary.Companion.PARTY
import com.cobblemon.mod.common.client.gui.summary.widgets.PartyWidget
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponent
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerComponentManager
import com.cobblemon.mod.common.client.integration.controlify.gui.component.ControllerScreen
import com.cobblemon.mod.common.client.integration.controlify.gui.component.SubManagerComponent
import com.cobblemon.mod.common.client.keybind.boundKey
import com.cobblemon.mod.common.client.keybind.keybinds.PartySendBinding
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.battle.RemoveSpectatorPacket
import com.cobblemon.mod.common.util.battleLang
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import org.joml.Vector4i

class BattleGUI : Screen(battleLang("gui.title")), CobblemonRenderable, ControllerScreen {
    companion object {
        const val OPTION_VERTICAL_SPACING = 3
        const val OPTION_HORIZONTAL_SPACING = 3
        const val OPTION_ROOT_X = 12
        const val OPTION_VERTICAL_OFFSET = 85

        val fightResource = cobblemonResource("textures/gui/battle/battle_menu_fight.png")
        val bagResource = cobblemonResource("textures/gui/battle/battle_menu_bag.png")
        val switchResource = cobblemonResource("textures/gui/battle/battle_menu_switch.png")
        val runResource = cobblemonResource("textures/gui/battle/battle_menu_run.png")
        val forfeitResource = cobblemonResource("textures/gui/battle/battle_menu_forfeit.png")
    }

    private lateinit var messagePane: BattleMessagePane
    var opacity = 0F
    val actor = CobblemonClient.battle?.side1?.actors?.find { it.uuid == Minecraft.getInstance().player?.uuid }
    val specBackButton = BattleBackButton(12f, Minecraft.getInstance().window.guiScaledHeight - 32f)

    var queuedActions = mutableListOf<() -> Unit>()

    val controllerComponentManager = ControllerComponentManager(this)

    override fun getComponentManager(): ControllerComponentManager {
        return controllerComponentManager
    }

    override fun init() {
        super.init()
        messagePane = BattleMessagePane(CobblemonClient.battle!!.messages)
        messagePane.opacity = CobblemonClient.battleOverlay.opacityRatio.toFloat().coerceAtLeast(0.3F)
        addRenderableWidget(messagePane)

        controllerComponentManager.componentList["spectating_quit"] = ControllerComponent().also {
            it.onPress = {
                quitSpectating()
            }
        }
        controllerComponentManager.componentList["battle_action"] = SubManagerComponent().also {
            it.subManager = { getCurrentActionSelection()?.controllerComponentManager }
        }
        controllerComponentManager.let {
            it.focused = it.componentList["battle_action"]
            it.onActiveChange = { state ->
                if (!state) {
                    getCurrentActionSelection()?.controllerComponentManager?.active = false
                } else {
                    if (it.currentSelectedComponent == "battle_action") getCurrentActionSelection()?.controllerComponentManager?.active = true
                }
            }
            it.onBack = {
                if (it.currentSelectedComponent == "battle_action") {
                    getCurrentActionSelection()?.controllerComponentManager?.onBack?.invoke()
                } else {
                    minimizeBattle()
                }
            }
        }
    }

    fun changeActionSelection(newSelection: BattleActionSelection?) {
        val current = children().find { it is BattleActionSelection }
        queuedActions.add {
            current?.let(this::removeWidget)
            if (newSelection != null) {
                addRenderableWidget(newSelection)
            }
            if (controllerComponentManager.currentSelectedComponent == "battle_action" && controllerComponentManager.active) {
                focused = null
                getCurrentActionSelection()?.controllerComponentManager?.active = true
                controllerComponentManager.focused?.onFocus?.invoke()
            }
        }
    }

    fun getCurrentActionSelection() = children().filterIsInstance<BattleActionSelection>().firstOrNull()

    fun removeInvalidBattleActionSelection() {
        children().filterIsInstance<BattleActionSelection>().firstOrNull()?.let {
            children().remove(it)
        }
    }

    fun selectAction(request: SingleActionRequest, response: ShowdownActionResponse?) {
        val battle = CobblemonClient.battle ?: return
        if (request.response == null) {
            request.response = response
            changeActionSelection(null)
            battle.checkForFinishedChoosing()
        }
    }

    override fun getSelectionPosAndSize(): Vector4i? {
        val data = super.getSelectionPosAndSize()
        if (data != null) {
            return data
        }

        val actionSelection = getCurrentActionSelection()
        var newData: Vector4i? = null
        when (actionSelection) {
            is BattleGeneralActionSelection -> {
                val tile = actionSelection.controllerComponentManager.focused?.associatedGUIElement as? BattleOptionTile
                if (tile != null) {
                    newData = Vector4i(tile.x - 2, tile.y - 2, BattleOptionTile.OPTION_WIDTH + 4, BattleOptionTile.OPTION_HEIGHT + 4)
                }
            }
            is BattleMoveSelection -> {
                val move = actionSelection.moveTiles[actionSelection.selectedMoveIndex]
                newData = Vector4i(move.x.toInt() - 10, move.y.toInt() - 2, BattleMoveSelection.MOVE_WIDTH + 12, BattleMoveSelection.MOVE_HEIGHT + 4)
            }
            is BattleSwitchPokemonSelection -> {
                val tile = actionSelection.tiles[actionSelection.selectedTileIndex]
                newData = Vector4i(tile.x.toInt() - 2, tile.y.toInt() - 2, BattleSwitchPokemonSelection.SwitchTile.SELECT_WIDTH + 2, BattleSwitchPokemonSelection.SwitchTile.SELECT_HEIGHT - 1)
            }
            is BattleTargetSelection -> {
                val tile = actionSelection.baseTiles[actionSelection.selectedTileIndex]
                newData = Vector4i(tile.x.toInt() - 2, tile.y.toInt() - 2, BattleTargetSelection.TARGET_WIDTH + 4, BattleTargetSelection.TARGET_HEIGHT + 2)
            }
        }

        return newData
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        super.render(context, mouseX, mouseY, delta)

        opacity = CobblemonClient.battleOverlay.opacityRatio.toFloat()
        children().filterIsInstance<BattleMessagePane>().forEach { it.opacity = opacity.coerceAtLeast(0.3F) }

        queuedActions.forEach { it() }
        queuedActions.clear()

        val battle = CobblemonClient.battle
        if (battle == null) {
            onClose()
            return
        } else if (CobblemonClient.battleOverlay.opacityRatio <= 0.1 && CobblemonClient.battle?.minimised == true) {
            onClose()
            return
        }

        if (actor != null) {
            if (battle.mustChoose) {
                if (getCurrentActionSelection() == null) {
                    val unanswered = battle.getFirstUnansweredRequest()
                    if (unanswered != null) {
                        changeActionSelection(deriveRootActionSelection(actor, unanswered))
                    }
                }
            } else if (getCurrentActionSelection() != null) {
                changeActionSelection(null)
            }
        }

        if (battle.spectating) {
            specBackButton.render(context, mouseX, mouseY, delta)
        }

        val currentSelection = getCurrentActionSelection()
        if (currentSelection == null || currentSelection is BattleGeneralActionSelection ) {
            drawScaledText(
                context = context,
                text = battleLang("ui.hide_label", PartySendBinding.boundKey().displayName),
                x = Minecraft.getInstance().window.guiScaledWidth / 2,
                y = (Minecraft.getInstance().window.guiScaledHeight / 5),
                opacity = 0.75F * opacity,
                centered = true
            )
        } else if (currentSelection is ForfeitConfirmationSelection) {
            drawScaledText(
                context = context,
                text = battleLang("ui.forfeit_confirmation", PartySendBinding.boundKey().displayName),
                x = Minecraft.getInstance().window.guiScaledWidth / 2,
                y = (Minecraft.getInstance().window.guiScaledHeight / 5),
                opacity = 0.75F * opacity,
                centered = true
            )
        }

        renderSelection(context.pose())

        val activeComponent: ControllerComponent?
        if (battle.spectating) {
            activeComponent = controllerComponentManager.componentList["spectator_quit"]
        } else {
            activeComponent = controllerComponentManager.componentList["battle_action"]
        }
        if (controllerComponentManager.focused != activeComponent) {
            controllerComponentManager.focused = activeComponent
        }

        queuedActions.forEach { it() }
        queuedActions.clear()
    }

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {

    }

    fun deriveRootActionSelection(actor: ClientBattleActor, request: SingleActionRequest): BattleActionSelection? {
        return if (request.forceSwitch) {
            BattleSwitchPokemonSelection(this, request)
        } else {
            // Known quirk of Showdown. It'll ask for actions on fainted slots
            // Also during a forced switch in doubles/triples it'll ask for actions on non-switching slots
            val pokemon = request.side?.pokemon?.firstOrNull { it.uuid == request.activePokemon.battlePokemon?.uuid }
            if (pokemon == null || pokemon.condition.contains("fnt") || pokemon.commanding || request.moveSet == null) {
                this.selectAction(request, PassActionResponse)
                null
            } else {
                BattleGeneralActionSelection(this, request)
            }
        }
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        super.onClose()
        CobblemonClient.battle?.minimised = true
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (this::messagePane.isInitialized) messagePane.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun charTyped(chr: Char, modifiers: Int): Boolean {
        if (chr.toString().equals(PartySendBinding.boundKey().displayName.string, ignoreCase = true) && CobblemonClient.battleOverlay.opacity == BattleOverlay.MAX_OPACITY && PartySendBinding.canAction()) {
            return minimizeBattle()
        }
        return super.charTyped(chr, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == PartySendBinding.boundKey().value && CobblemonClient.battleOverlay.opacity == BattleOverlay.MAX_OPACITY && PartySendBinding.canAction()) {
            return minimizeBattle()
        }

        if (specBackButton.isHovered(mouseX, mouseY)) {
            quitSpectating()
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    fun quitSpectating() {
        val battle = CobblemonClient.battle
        if (battle?.spectating == true) {
            RemoveSpectatorPacket(battle.battleId).sendToServer()
            CobblemonClient.endBattle()
        }
    }

    fun minimizeBattle(): Boolean {
        val battle = CobblemonClient.battle ?: return false
        battle.minimised = !battle.minimised
        PartySendBinding.actioned()
        return true
    }
}