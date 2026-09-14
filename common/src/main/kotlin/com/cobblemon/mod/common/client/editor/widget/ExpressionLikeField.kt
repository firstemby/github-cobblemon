/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.widget

import imgui.ImGui
import imgui.type.ImString
import net.minecraft.client.resources.language.I18n
import net.minecraft.resources.ResourceLocation

class ExpressionLikeField(
    private val label: String,
    initialValue: String = ""
) {
    private val buffer = ImString(512).apply { inputData.isResizable = true; set(initialValue) }
    private var scriptMode: Boolean = looksLikeResourceLocation(initialValue)

    fun render(): String {
        ImGui.pushID(label)

        try {
            val mode = if (scriptMode) "script" else "inline"
            if (ImGui.smallButton(I18n.get("cobblemon.cinematic_editor.$mode") + "###mode")) {
                scriptMode = !scriptMode
            }
            ImGui.sameLine()

            if (scriptMode) {
                ImGui.inputText("##$label", buffer)
                ImGui.sameLine()
                ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.script_id"))
            } else {
                ImGui.inputText(label, buffer)
            }
        } finally {
            ImGui.popID()
        }
        return buffer.get()
    }

    fun getValue(): String = buffer.get()

    companion object {
        internal fun looksLikeResourceLocation(value: String) = ':' in value && ResourceLocation.tryParse(value) != null
    }
}
