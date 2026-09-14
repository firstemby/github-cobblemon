/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import imgui.ImGui
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.language.I18n

object SetFromCameraButton {

    data class CameraPose(
        val x: Double, val y: Double, val z: Double,
        val yaw: Float, val pitch: Float
    )

    fun render(idSuffix: String, onCaptured: (CameraPose) -> Unit) {
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.use_camera") + "##$idSuffix")) {
            val mc = Minecraft.getInstance()
            if (mc.player == null) return
            val cam = mc.gameRenderer.mainCamera
            onCaptured(
                CameraPose(
                    x = cam.position.x,
                    y = cam.position.y,
                    z = cam.position.z,
                    yaw = cam.yRot,
                    pitch = cam.xRot
                )
            )
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip()
            ImGui.text(I18n.get("cobblemon.cinematic_editor.use_camera_hint"))
            ImGui.endTooltip()
        }
    }
}
