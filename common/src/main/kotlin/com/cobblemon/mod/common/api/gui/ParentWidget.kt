/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.gui

import com.cobblemon.mod.common.client.gui.CobblemonRenderable
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

/**
 * This class adds children-awareness to a Widget similar like a Screen
 * (otherwise the Widgets do not react on click/hover)
 */
abstract class ParentWidget(
    pX: Int, pY: Int,
    pWidth: Int, pHeight: Int,
    component: Component
): CobblemonRenderable, AbstractWidget(pX, pY, pWidth, pHeight, component) {

    val children: MutableList<GuiEventListener> = mutableListOf()

    /**
     * Adds Widget to the children list
     */
    protected fun addWidget(widget: GuiEventListener) {
        children.add(widget)
    }

    /**
     * Removes Widget from the children list
     */
    protected fun removeWidget(widget: GuiEventListener) {
        children.remove(widget)
    }

    override fun mouseMoved(pMouseX: Double, pMouseY: Double) {
        children.forEach {
            it.mouseMoved(pMouseX, pMouseY)
        }
        super.mouseMoved(pMouseX, pMouseY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        return children.any {
            it.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
        } || super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseButtonEvent: MouseButtonEvent, doubleClick: Boolean): Boolean {
        return children.any {
            it.mouseClicked(mouseButtonEvent, false)
        } || super.mouseClicked(mouseButtonEvent, false)
    }

    override fun mouseReleased(mouseButtonEvent: MouseButtonEvent): Boolean {
        return children.any {
            it.mouseReleased(mouseButtonEvent)
        } || super.mouseReleased(mouseButtonEvent)
    }

    override fun mouseDragged(mouseButtonEvent: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        return children.any {
            it.mouseDragged(mouseButtonEvent, dragX, dragY)
        } || super.mouseDragged(mouseButtonEvent, dragX, dragY)
    }

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        return children.any {
            it.keyPressed(keyEvent)
        } || super.keyPressed(keyEvent)
    }

    override fun keyReleased(keyEvent: KeyEvent): Boolean {
        children.forEach {
            it.keyReleased(keyEvent)
        }
        return super.keyReleased(keyEvent)
    }

    override fun charTyped(characterEvent: CharacterEvent): Boolean {
        children.forEach {
            it.charTyped(characterEvent)
        }
        return super.charTyped(characterEvent)
    }

    override fun defaultButtonNarrationText(pNarrationElementOutput: NarrationElementOutput) {
    }

    /**
     * TODO
     *
     * @param mouseX
     * @param mouseY
     * @return
     *
     * @author Licious
     * @since April 29th, 2022
     */
    fun isHovered(mouseX: Number, mouseY: Number) = mouseX in this.x..(this.x + this.width) && mouseY in this.y..(this.y + this.height)

    override fun updateWidgetNarration(builder: NarrationElementOutput) {}

}
