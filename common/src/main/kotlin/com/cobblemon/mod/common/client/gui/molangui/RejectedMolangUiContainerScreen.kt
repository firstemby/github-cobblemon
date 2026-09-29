/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.molangui

import com.cobblemon.mod.common.api.molangui.container.MolangContainerMenu
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.world.entity.player.Inventory

internal class RejectedMolangUiContainerScreen(menu: MolangContainerMenu, inventory: Inventory) :
    AbstractContainerScreen<MolangContainerMenu>(menu, inventory, lang("molang_ui.invalid_view")) {
    override fun containerTick() {
        minecraft?.player?.closeContainer()
    }

    override fun render(graphics: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        graphics.drawString(font, title, 8, 8, 0xFFFFFF)
    }

    override fun renderBg(graphics: GuiGraphics, delta: Float, mouseX: Int, mouseY: Int) {}
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int) = true
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int) = true
    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double) = true
}
