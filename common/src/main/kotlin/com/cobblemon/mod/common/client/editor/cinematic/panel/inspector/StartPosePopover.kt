/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActorDirection
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorStartPose
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

object StartPosePopover {
    private val xBuf = ImFloat(); private val yBuf = ImFloat(); private val zBuf = ImFloat()
    private val yawBuf = ImFloat(); private val pitchBuf = ImFloat()
    private val blendBuf = ImFloat(0.5f)
    private val editedAxes = mutableSetOf<String>()
    private var syncedKey: String? = null
    private var syncedScene: EditorScene? = null
    private var syncedDirection: EditorActorDirection? = null
    private var syncedPose: EditorStartPose? = null

    fun open(popupId: String) { syncedKey = null; ImGui.openPopup(popupId) }

    fun render(
        popupId: String,
        syncKey: String,
        scene: EditorScene,
        direction: EditorActorDirection?,
        onApply: (EditorStartPose?) -> Unit
    ) {
        if (!ImGui.beginPopup(popupId)) return
        try {
            if (syncedKey != syncKey || syncedScene !== scene || syncedDirection !== direction || syncedPose !== direction?.startPose) {
                val sp = direction?.startPose
                xBuf.set(bufferValue(sp?.position?.x, 0f))
                yBuf.set(bufferValue(sp?.position?.y, 64f))
                zBuf.set(bufferValue(sp?.position?.z, 0f))
                yawBuf.set(sp?.rotation?.yaw ?: 0f)
                pitchBuf.set(sp?.rotation?.pitch ?: 0f)
                blendBuf.set(sp?.blendDurationSeconds ?: 0.5f)
                editedAxes.clear()
                syncedKey = syncKey
                syncedScene = scene
                syncedDirection = direction
                syncedPose = sp
            }

            ImGui.text(I18n.get("cobblemon.cinematic_editor.start_pose.position"))
            for ((axis, buffer) in listOf("x" to xBuf, "y" to yBuf, "z" to zBuf)) {
                if (axis != "x") ImGui.sameLine()
                ImGui.setNextItemWidth(70f)
                if (inputFiniteFloat("$axis##sp$axis$popupId", buffer)) editedAxes.add(axis)
            }
            ImGui.text(I18n.get("cobblemon.cinematic_editor.start_pose.rotation"))
            ImGui.setNextItemWidth(70f)
            inputFiniteFloat(I18n.get("cobblemon.cinematic_editor.start_pose.yaw") + "##spYaw$popupId", yawBuf)
            ImGui.sameLine(); ImGui.setNextItemWidth(70f)
            inputFiniteFloat(I18n.get("cobblemon.cinematic_editor.start_pose.pitch") + "##spPitch$popupId", pitchBuf)
            ImGui.text(I18n.get("cobblemon.cinematic_editor.start_pose.blend"))
            ImGui.setNextItemWidth(80f); inputFiniteFloat("##spBlend$popupId", blendBuf)

            ImGui.separator()
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.start_pose.from_camera") + "##spCam$popupId")) {
                val cam = Minecraft.getInstance().gameRenderer.mainCamera
                val values = listOf(cam.position.x.toFloat(), cam.position.y.toFloat(), cam.position.z.toFloat(), cam.yRot, cam.xRot)
                if (values.all { it.isFinite() }) {
                    xBuf.set(values[0]); yBuf.set(values[1]); zBuf.set(values[2])
                    yawBuf.set(values[3]); pitchBuf.set(values[4])
                    editedAxes.addAll(listOf("x", "y", "z"))
                }
            }
            ImGui.sameLine()
            ImGui.beginDisabled(listOf(xBuf, yBuf, zBuf, yawBuf, pitchBuf, blendBuf).any { !it.get().isFinite() })
            try {
                if (ImGui.button(I18n.get("cobblemon.cinematic_editor.start_pose.apply") + "##spApply$popupId")) {
                    val position = mapOf("x" to xBuf.get(), "y" to yBuf.get(), "z" to zBuf.get()).filterKeys { it in editedAxes }
                    createEditedPose(syncedPose, position, yawBuf.get(), pitchBuf.get(), blendBuf.get())?.let(onApply)
                    syncedKey = null
                    ImGui.closeCurrentPopup()
                }
            } finally { ImGui.endDisabled() }
            ImGui.sameLine()
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.start_pose.clear") + "##spClear$popupId") && direction?.startPose != null) {
                onApply(null)
                syncedKey = null
                ImGui.closeCurrentPopup()
            }
        } finally { ImGui.endPopup() }
    }

    internal fun createEditedPose(
        original: EditorStartPose?, position: Map<String, Float>, yaw: Float, pitch: Float, blend: Float
    ): EditorStartPose? {
        if (position.values.any { !it.isFinite() } || !yaw.isFinite() || !pitch.isFinite() || !blend.isFinite()) return null
        fun coordinate(axis: String, previous: CoordinateValue?, default: Double): CoordinateValue =
            position[axis]?.let { CoordinateValue.Static(it.toDouble()) } ?: previous ?: CoordinateValue.Static(default)
        val nextPosition = PositionExpression(
            coordinate("x", original?.position?.x, 0.0),
            coordinate("y", original?.position?.y, 64.0),
            coordinate("z", original?.position?.z, 0.0)
        )
        val nextRotation = CameraRotation(yaw, pitch)
        val nextBlend = blend.coerceAtLeast(0f)
        if (original != null && nextPosition == original.position && nextRotation == original.rotation && nextBlend == original.blendDurationSeconds) return original
        return EditorStartPose(nextPosition, nextRotation, nextBlend)
    }

    private fun bufferValue(coordinate: CoordinateValue?, default: Float): Float =
        (coordinate as? CoordinateValue.Static)?.value?.toFloat()?.takeIf { it.isFinite() } ?: default
}
