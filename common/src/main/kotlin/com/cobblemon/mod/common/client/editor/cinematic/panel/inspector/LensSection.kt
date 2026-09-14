/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorLens
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeLensAspectCrop
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeLensLetterbox
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.SetShotLens
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImBoolean
import imgui.type.ImFloat

class LensSection {

    private val aspectCropBuf = ImFloat(0f)
    private val aspectEnabledBool = ImBoolean(false)
    private val letterboxBuf = FloatArray(1)
    private var lastScene: EditorScene? = null
    private var lastShotId: String? = null
    private var lastGeneration = -1

    fun render(scene: EditorScene, shotId: String) {
        val shot = scene.shots[shotId] ?: return

        val lensEnabledBool = ImBoolean(shot.lens != null)
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.lens_control.enabled") + "##${shotId}_lensEnable", lensEnabledBool)) {
            if (lensEnabledBool.get()) {
                scene.execute(SetShotLens(shotId, oldLens = null, newLens = EditorLens()))
            } else {
                scene.execute(SetShotLens(shotId, oldLens = shot.lens, newLens = null))
            }
        }

        val lens = shot.lens ?: return

        if (lastScene !== scene || lastShotId != shotId || lastGeneration != scene.editGeneration) {
            aspectEnabledBool.set(lens.aspectCrop != null)
            aspectCropBuf.set(lens.aspectCrop?.takeIf { it.isFinite() }?.coerceAtLeast(0.1f) ?: 2.35f)
            letterboxBuf[0] = lens.letterbox.takeIf { it.isFinite() }?.coerceIn(0f, 0.5f) ?: 0f
            lastScene = scene
            lastShotId = shotId
            lastGeneration = scene.editGeneration
        }

        ImGui.indent()

        try {
            if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.lens_control.aspect_crop") + "##${shotId}_lensAspectToggle", aspectEnabledBool)) {
                val old = lens.aspectCrop
                val newValue = if (aspectEnabledBool.get()) aspectCropBuf.get().coerceAtLeast(0.1f) else null
                scene.execute(ChangeLensAspectCrop(shotId, oldValue = old, newValue = newValue))
            }
            if (lens.aspectCrop != null) {
                ImGui.setNextItemWidth(120f)
                if (inputFiniteFloat("##${shotId}_lensAspect", aspectCropBuf)) {
                    val newValue = aspectCropBuf.get().coerceAtLeast(0.1f)
                    if (newValue != lens.aspectCrop) {
                        scene.execute(ChangeLensAspectCrop(shotId, oldValue = lens.aspectCrop, newValue = newValue))
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.lens_control.aspect_help"))
                }
            } else {
                ImGui.sameLine()
                ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.lens_control.native"))
            }

            ImGui.text(I18n.get("cobblemon.cinematic_editor.lens_control.letterbox"))
            ImGui.setNextItemWidth(200f)
            if (sliderFiniteFloat("##${shotId}_lensLetterbox", letterboxBuf, 0f, 0.5f)) {
                val newValue = letterboxBuf[0].coerceIn(0f, 0.5f)
                if (newValue != lens.letterbox) {
                    scene.execute(ChangeLensLetterbox(shotId, oldValue = lens.letterbox, newValue = newValue))
                }
            }
            if (ImGui.isItemHovered()) {
                ImGui.setTooltip(I18n.get("cobblemon.cinematic_editor.lens_control.letterbox_help"))
            }

        } finally {
            ImGui.unindent()
        }
    }
}
