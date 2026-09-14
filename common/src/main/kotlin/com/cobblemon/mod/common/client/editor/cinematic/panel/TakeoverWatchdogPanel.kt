/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel

import com.cobblemon.mod.common.client.editor.cinematic.TakeoverWatchdog
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import net.minecraft.client.resources.language.I18n

class TakeoverWatchdogPanel {

    private val visible = ImBoolean(false)

    fun setVisible(v: Boolean) { visible.set(v) }
    fun isVisible(): Boolean = visible.get()

    fun render() {
        if (!visible.get()) return
        if (ImGui.begin(I18n.get("cobblemon.cinematic_editor.view.watchdog") + "###Takeover Watchdog Log", visible, ImGuiWindowFlags.None)) {
            val entries = TakeoverWatchdog.entries()
            ImGui.text(I18n.get("cobblemon.cinematic_editor.watchdog.entries", entries.size))
            ImGui.sameLine()
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.watchdog.clear"))) TakeoverWatchdog.clear()
            ImGui.separator()

            if (entries.isEmpty()) {
                ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.watchdog.empty"))
            } else {
                for ((index, e) in entries.withIndex()) {
                    ImGui.pushID("##wd_$index")
                    val kindLabel = when (e.kind) {
                        TakeoverWatchdog.LogEntry.Kind.FORCED_EXIT -> I18n.get("cobblemon.cinematic_editor.watchdog.forced_exit")
                        TakeoverWatchdog.LogEntry.Kind.SAFE_MODE_APPLIED -> I18n.get("cobblemon.cinematic_editor.watchdog.safe_mode")
                    }
                    ImGui.text(I18n.get("cobblemon.cinematic_editor.watchdog.entry", e.timestamp, kindLabel, e.message.string))
                    if (e.stackTrace != null && ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.watchdog.stack_trace") + "###stack_trace_$index")) {
                        ImGui.textWrapped(e.stackTrace)
                    }
                    ImGui.popID()
                    ImGui.separator()
                }
            }
        }
        ImGui.end()
    }
}
