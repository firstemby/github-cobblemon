/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.interact.wheel

import com.google.common.collect.Multimap
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component

class PokeSkillWheelGUI(
    private val options: Multimap<Orientation, InteractWheelOption>,
    title: Component
) : InteractWheelGUI(options, title) {

    /**
     * Due to the button being held swallow the event
     */
    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) return false
        return super.mouseClicked(mouseX, mouseY, button)
    }

    /**
     * On release will close the wheel and trigger the button press of the hovered button
     * TODO: make this wheel choose more like pizza slices.
     */
    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            // Manually trigger the hovered button's press
            buttons.forEach { btn ->
                if (btn.isMouseOver(mouseX, mouseY)) {
                    btn.onClick(mouseX, mouseY)
                    return true
                }
            }
            Minecraft.getInstance().setScreen(null)
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

}
