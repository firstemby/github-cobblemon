package com.cobblemon.mod.common.client.gui.stadium

import com.cobblemon.mod.common.client.CobblemonClient
import com.cobblemon.mod.common.client.gui.battle.BattleGUI
import com.cobblemon.mod.common.client.keybind.keybinds.StadiumSpectateBinding
import com.cobblemon.mod.common.client.net.stadium.StadiumSpectateStateHandler
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class StadiumSpectatorScreen : Screen(Component.translatable("key.cobblemon.stadium_spectate")) {
    override fun init() {
        addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium.spectate.leave")) {
            StadiumSpectateStateHandler.toggle()
        }.bounds(width / 2 - 75, height - 30, 150, 20).build())
        if (CobblemonClient.battle?.spectating == true) {
            addRenderableWidget(Button.builder(Component.translatable("cobblemon.stadium.spectate.details")) {
                CobblemonClient.battle?.minimised = false
                minecraft?.setScreen(BattleGUI())
            }.bounds(width / 2 - 75, height - 54, 150, 20).build())
        }
    }

    override fun renderBackground(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {}
    override fun isPauseScreen() = false
    override fun onClose() { StadiumSpectateStateHandler.toggle() }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (StadiumSpectateBinding.matches(keyCode, scanCode)) {
            StadiumSpectateStateHandler.toggle()
            return true
        }
        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (StadiumSpectateBinding.matchesMouse(button)) {
            StadiumSpectateStateHandler.toggle()
            return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }
}
