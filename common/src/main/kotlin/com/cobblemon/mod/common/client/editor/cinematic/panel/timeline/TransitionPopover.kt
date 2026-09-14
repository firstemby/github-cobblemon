/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

object TransitionPopover {
    private val blendBuf = ImFloat(0.4f)
    private var lastOpenedPopupId: String? = null

    fun open(popupId: String) {
        ImGui.openPopup(popupId)
        lastOpenedPopupId = popupId
    }

    fun render(popupId: String, current: CutTransition, onPick: (CutTransition) -> Unit): Boolean {
        if (!ImGui.beginPopup(popupId)) return false

        if (popupId == lastOpenedPopupId) {
            val currentBlend = (current as? CutTransition.Blend)?.duration ?: 0.4f
            blendBuf.set(currentBlend)
            lastOpenedPopupId = null
        }

        if (ImGui.radioButton(I18n.get("cobblemon.cinematic_editor.timeline.instant"), current is CutTransition.Instant)) {
            if (current !is CutTransition.Instant) onPick(CutTransition.Instant)
        }
        if (ImGui.radioButton(I18n.get("cobblemon.cinematic_editor.timeline.blend"), current is CutTransition.Blend) && blendBuf.get().isFinite()) {
            val newBlend = CutTransition.Blend(blendBuf.get().coerceAtLeast(0.01f))
            if (current !is CutTransition.Blend) onPick(newBlend)
        }
        if (current is CutTransition.Blend) {
            ImGui.sameLine(); ImGui.setNextItemWidth(80f)
            if (ImGui.inputFloat(I18n.get("cobblemon.cinematic_editor.timeline.blend_duration") + "##popoverBlend", blendBuf) && blendBuf.get().isFinite()) {
                val v = blendBuf.get().coerceAtLeast(0.01f); blendBuf.set(v)
                onPick(CutTransition.Blend(v))
            }
        }

        ImGui.endPopup()
        return true
    }
}
