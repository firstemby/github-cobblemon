/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.widget

import com.cobblemon.mod.common.api.tween.EasingRegistry
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class EasingDropdown(
    private val label: String
) {
    fun render(currentValue: String): String {
        var result = currentValue

        drawCurvePreview(currentValue)
        ImGui.sameLine()

        if (ImGui.beginCombo(label, displayName(currentValue))) {
            for (name in EasingRegistry.getRegisteredNames().sorted()) {
                val isSelected = name == currentValue
                ImGui.pushID(name)
                if (ImGui.selectable(displayName(name), isSelected)) {
                    result = name
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
                ImGui.popID()
            }
            ImGui.endCombo()
        }

        return result
    }

    private fun displayName(name: String): String {
        val key = "cobblemon.cinematic_editor.easing.$name"
        return if (I18n.exists(key)) I18n.get(key) else name
    }

    private fun drawCurvePreview(easingName: String) {
        val easingFn = EasingRegistry.getEasingFunction(easingName)
        val previewW = 32f
        val previewH = 16f

        val cursorX = ImGui.getCursorScreenPosX()
        val cursorY = ImGui.getCursorScreenPosY()
        val drawList = ImGui.getWindowDrawList()

        val bgColor = packColor(40, 40, 40, 200)
        val lineColor = packColor(100, 200, 255, 255)
        drawList.addRectFilled(cursorX, cursorY, cursorX + previewW, cursorY + previewH, bgColor)

        val segments = 16
        val values = (0..segments).map { easingFn(it.toFloat() / segments) }
        if (values.any { !it.isFinite() }) {
            ImGui.dummy(previewW, previewH)
            return
        }
        val minimum = minOf(0f, values.min())
        val maximum = maxOf(1f, values.max())
        for (i in 0 until segments) {
            val t0 = i.toFloat() / segments
            val t1 = (i + 1).toFloat() / segments
            val v0 = (values[i] - minimum) / (maximum - minimum)
            val v1 = (values[i + 1] - minimum) / (maximum - minimum)

            val x0 = cursorX + t0 * previewW
            val y0 = cursorY + previewH - v0 * previewH
            val x1 = cursorX + t1 * previewW
            val y1 = cursorY + previewH - v1 * previewH

            drawList.addLine(x0, y0, x1, y1, lineColor)
        }

        ImGui.dummy(previewW, previewH)
    }

    companion object {
        private fun packColor(r: Int, g: Int, b: Int, a: Int): Int {
            return (a shl 24) or (b shl 16) or (g shl 8) or r
        }
    }
}
