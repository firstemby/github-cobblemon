/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.stadium.widget

import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.client.gui.stadium.StadiumDropdownLayout
import com.cobblemon.mod.common.client.gui.stadium.StadiumSkin
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import org.lwjgl.glfw.GLFW

class StadiumDropdown<T>(
    x: Int, y: Int, width: Int, height: Int, private val label: String,
    private val options: List<T>, current: T, private val display: (T) -> String,
    private val opened: (StadiumDropdown<*>) -> Unit, private val change: (T) -> Unit
) : StadiumButton(x, y, width, height, Component.empty(), onPress = {}) {
    private var selected = options.indexOf(current)
    private var highlighted = selected.coerceAtLeast(0)
    private var first = 0
    private var typedPrefix = ""
    private var typedAt = 0L
    private var keyboardNavigation = false
    private var lastMouseX = Int.MIN_VALUE
    private var lastMouseY = Int.MIN_VALUE
    var isOpen = false
        private set

    init {
        active = options.isNotEmpty()
        updateLabel()
    }

    private fun updateLabel() {
        message = Component.literal(options.getOrNull(selected)?.let(display) ?: if (options.isEmpty()) I18n.get("cobblemon.stadium.dropdown.no_options_available") else I18n.get("cobblemon.stadium.dropdown.select_an_option"))
        tooltip = Tooltip.create(Component.literal("$label: ${message.string}"))
    }

    private fun layout(): StadiumDropdownLayout {
        val client = Minecraft.getInstance()
        val desired = maxOf(width, (options.maxOfOrNull { client.font.width(display(it)) } ?: 0) + 30)
        return StadiumDropdownLayout.fit(x, y, width, height, desired, options.size, client.window.guiScaledWidth, client.window.guiScaledHeight)
    }

    override fun onPress() {
        if (isOpen) close() else if (active) {
            opened(this)
            isOpen = true
            tooltip = null
            highlighted = selected.coerceAtLeast(0)
            typedPrefix = ""
            revealHighlight()
        }
    }

    fun close() { isOpen = false; updateLabel() }

    private fun revealHighlight() {
        val rows = layout().visibleRows
        first = first.coerceIn((highlighted - rows + 1).coerceAtLeast(0), highlighted)
            .coerceIn(0, (options.size - rows).coerceAtLeast(0))
    }

    private fun choose(index: Int) {
        selected = index
        close()
        updateLabel()
        change(options[index])
    }

    fun popupClick(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isOpen) return false
        val popup = layout()
        if (button == 0 && popup.contains(mouseX, mouseY)) {
            popup.rowAt(mouseY)?.let { choose(first + it) }
        } else close()
        return true
    }

    fun popupScroll(amount: Double): Boolean {
        val rows = layout().visibleRows
        val steps = if (amount == 0.0) 0 else if (amount > 0) amount.toInt().coerceAtLeast(1) else amount.toInt().coerceAtMost(-1)
        first = (first - steps).coerceIn(0, (options.size - rows).coerceAtLeast(0))
        highlighted = highlighted.coerceIn(first, (first + rows - 1).coerceAtMost(options.lastIndex))
        return true
    }

    override fun charTyped(codePoint: Char, modifiers: Int): Boolean {
        if (!isOpen) return false
        keyboardNavigation = true
        val now = System.currentTimeMillis()
        typedPrefix = (if (now - typedAt > 1000) "" else typedPrefix) + codePoint
        typedAt = now
        val match = options.indexOfFirst { display(it).startsWith(typedPrefix, true) || it.toString().startsWith(typedPrefix, true) }
        if (match >= 0) { highlighted = match; revealHighlight() }
        return true
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!active) return false
        if (!isOpen) {
            if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) { onPress(); return true }
            return super.keyPressed(keyCode, scanCode, modifiers)
        }
        keyboardNavigation = true
        when (keyCode) {
            GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB -> close()
            GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> choose(highlighted)
            GLFW.GLFW_KEY_UP -> highlighted = (highlighted - 1).coerceAtLeast(0)
            GLFW.GLFW_KEY_DOWN -> highlighted = (highlighted + 1).coerceAtMost(options.lastIndex)
            GLFW.GLFW_KEY_HOME -> highlighted = 0
            GLFW.GLFW_KEY_END -> highlighted = options.lastIndex
            GLFW.GLFW_KEY_PAGE_UP -> highlighted = (highlighted - layout().visibleRows).coerceAtLeast(0)
            GLFW.GLFW_KEY_PAGE_DOWN -> highlighted = (highlighted + layout().visibleRows).coerceAtMost(options.lastIndex)
            else -> return true
        }
        if (isOpen) revealHighlight()
        return keyCode != GLFW.GLFW_KEY_TAB
    }

    override fun createNarrationMessage(): MutableComponent {
        val value = if (isOpen) options.getOrNull(highlighted)?.let(display).orEmpty() else message.string
        return Component.translatable(if (isOpen) "cobblemon.stadium.dropdown.narration_open" else "cobblemon.stadium.dropdown.narration_closed", label, value)
    }

    override fun renderWidget(graphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val font = Minecraft.getInstance().font
        StadiumSkin.panel(graphics, if (active && (isOpen || isHoveredOrFocused)) StadiumSkin.Surface.SELECTED else StadiumSkin.Surface.CONTROL, x, y, width, height, 3)
        val textColor = if (!active) StadiumSkin.MUTED else if (isOpen || isHoveredOrFocused) StadiumSkin.TEXT else StadiumSkin.INK
        graphics.drawString(font, font.plainSubstrByWidth(message.string, width - 24), x + 7, y + (height - 8) / 2, textColor, false)
        for (line in 0..2) {
            val arrowY = y + height / 2 + if (isOpen) 1 - line else line - 1
            graphics.hLine(x + width - 13 + line, x + width - 9 - line, arrowY, textColor)
        }
        if (isFocused) graphics.renderOutline(x, y, width, height, StadiumSkin.FOCUS)
    }

    fun renderPopup(graphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isOpen) return
        val popup = layout()
        val font = Minecraft.getInstance().font
        if (mouseX != lastMouseX || mouseY != lastMouseY) keyboardNavigation = false
        lastMouseX = mouseX
        lastMouseY = mouseY
        graphics.pose().pushPose()
        try {
            graphics.pose().translate(0f, 0f, 2000f)
            StadiumSkin.panel(graphics, StadiumSkin.Surface.CONTROL, popup.x, popup.y, popup.width, popup.height)
            val hovered = if (!keyboardNavigation && popup.contains(mouseX.toDouble(), mouseY.toDouble())) popup.rowAt(mouseY.toDouble())?.plus(first) else null
            repeat(popup.visibleRows) { row ->
                val index = first + row
                val top = popup.y + StadiumDropdownLayout.PADDING + row * StadiumDropdownLayout.ROW_HEIGHT
                if (index == (hovered ?: highlighted)) StadiumSkin.panel(graphics, StadiumSkin.Surface.SELECTED, popup.x + 3, top, popup.width - 6, StadiumDropdownLayout.ROW_HEIGHT, 2)
                if (index == selected) graphics.drawString(font, ">", popup.x + 7, top + 6, StadiumSkin.FOCUS, false)
                graphics.drawString(font, font.plainSubstrByWidth(display(options[index]), popup.width - 30), popup.x + 18, top + 6,
                    if (index == (hovered ?: highlighted)) StadiumSkin.TEXT else StadiumSkin.INK, false)
            }
            if (options.size > popup.visibleRows) {
                val trackHeight = popup.height - 8
                val thumb = (trackHeight * popup.visibleRows / options.size).coerceAtLeast(6)
                val top = popup.y + 4 + (trackHeight - thumb) * first / (options.size - popup.visibleRows)
                graphics.fill(popup.x + popup.width - 4, top, popup.x + popup.width - 2, top + thumb, StadiumSkin.FOCUS)
            }
        } finally {
            graphics.pose().popPose()
        }
    }

}
