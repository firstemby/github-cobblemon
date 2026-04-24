/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.pokedex.page

import com.cobblemon.mod.common.client.gui.pokedex.PokedexGUI
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.resources.ResourceLocation

interface PokedexGuiPage {
    val id: ResourceLocation
    val titleKey: String

    fun onSelected(gui: PokedexGUI) {}
    fun onClose(gui: PokedexGUI) {}

    fun render(gui: PokedexGUI, context: GuiGraphics, x: Int, y: Int, mouseX: Int, mouseY: Int, delta: Float) {}
    fun mouseClicked(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseReleased(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int): Boolean = false
    fun mouseDragged(gui: PokedexGUI, mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean = false
    fun mouseScrolled(gui: PokedexGUI, mouseX: Double, mouseY: Double, deltaX: Double, deltaY: Double): Boolean = false
    fun keyPressed(gui: PokedexGUI, keyCode: Int, scanCode: Int, modifiers: Int): Boolean = false
    fun charTyped(gui: PokedexGUI, codePoint: Char, modifiers: Int): Boolean = false
    fun tick(gui: PokedexGUI) {}
}
