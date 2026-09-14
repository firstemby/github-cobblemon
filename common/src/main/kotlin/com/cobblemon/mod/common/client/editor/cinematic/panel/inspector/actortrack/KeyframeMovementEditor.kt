/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import net.minecraft.client.Minecraft
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SetFromCameraButton
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inputFiniteFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorObject
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import net.minecraft.world.phys.Vec3

class KeyframeMovementEditor {

    private val timeBufs = mutableListOf<ImFloat>()
    private val xBufs = mutableListOf<ImFloat>()
    private val yBufs = mutableListOf<ImFloat>()
    private val zBufs = mutableListOf<ImFloat>()
    private var lastSeenGeneration: Int = -1
    private var syncedData: JsonObject? = null

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val kfArray = data.get("keyframes").trackArray() ?: JsonArray()
        if (data !== syncedData || scene.editGeneration != lastSeenGeneration) {
            resetBuffers()
            syncedData = data
            lastSeenGeneration = scene.editGeneration
        }
        sync(kfArray)

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.keyframes", kfArray.size()))
        ImGui.separator()
        ImGui.text("#"); ImGui.sameLine(40f); ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.time"))
        ImGui.sameLine(100f); ImGui.text("x")
        ImGui.sameLine(160f); ImGui.text("y")
        ImGui.sameLine(220f); ImGui.text("z")

        var rowToRemove = -1
        val idBase = "${beatId}_${actorId}_${trackIndex}"
        for (i in 0 until kfArray.size()) {
            ImGui.pushID("kfmv_${idBase}_$i")
            try {
                ImGui.text(i.toString())
                ImGui.sameLine(40f); ImGui.setNextItemWidth(55f)
                if (inputFiniteFloat("##t", timeBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("keyframes").trackArray()?.trackRow(i)?.addProperty("time", timeBufs[i].get().coerceAtLeast(0f)) }
                ImGui.sameLine(100f); ImGui.setNextItemWidth(55f)
                if (inputFiniteFloat("##x", xBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("keyframes").trackArray()?.trackRow(i)?.trackObject("position")?.addProperty("x", xBufs[i].get().toDouble()) }
                ImGui.sameLine(160f); ImGui.setNextItemWidth(55f)
                if (inputFiniteFloat("##y", yBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("keyframes").trackArray()?.trackRow(i)?.trackObject("position")?.addProperty("y", yBufs[i].get().toDouble()) }
                ImGui.sameLine(220f); ImGui.setNextItemWidth(55f)
                if (inputFiniteFloat("##z", zBufs[i])) editActorTrack(scene, beatId, actorId, trackIndex, track) { it.get("keyframes").trackArray()?.trackRow(i)?.trackObject("position")?.addProperty("z", zBufs[i].get().toDouble()) }

                ImGui.sameLine()
                SetFromCameraButton.render("kfmv_cam_${idBase}_$i") { cam ->
                    if (listOf(cam.x, cam.y, cam.z).all { it.toFloat().isFinite() }) {
                        editActorTrack(scene, beatId, actorId, trackIndex, track) {
                            it.get("keyframes").trackArray()?.trackRow(i)?.trackObject("position")?.apply {
                                addProperty("x", cam.x); addProperty("y", cam.y); addProperty("z", cam.z)
                            }
                        }
                    }
                }
                ImGui.sameLine()
                if (ImGui.button("\u2715##rm")) rowToRemove = i
            } finally {
                ImGui.popID()
            }
        }

        if (rowToRemove >= 0) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { removeKeyframe(it, rowToRemove) }
            resetBuffers()
        }

        ImGui.spacing()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.add_blank") + "##${idBase}_blank")) {
            editActorTrack(scene, beatId, actorId, trackIndex, track) { appendKeyframe(it, 0f, Vec3.ZERO) }
            resetBuffers()
        }
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.actor_track.add_at_camera") + "##${idBase}_cam")) {
            val mc = Minecraft.getInstance()
            val cam = mc.gameRenderer.mainCamera
            editActorTrack(scene, beatId, actorId, trackIndex, track) { appendKeyframe(it, 0f, cam.position) }
            resetBuffers()
        }
    }

    private fun sync(arr: JsonArray) {
        while (timeBufs.size < arr.size()) {
            val i = timeBufs.size
            val kf = arr[i].inspectorObject()
            val pos = kf?.get("position").inspectorObject()
            timeBufs += ImFloat(kf?.get("time").inspectorFloat().coerceAtLeast(0f))
            xBufs += ImFloat(pos?.get("x").inspectorFloat())
            yBufs += ImFloat(pos?.get("y").inspectorFloat())
            zBufs += ImFloat(pos?.get("z").inspectorFloat())
        }
        while (timeBufs.size > arr.size()) {
            timeBufs.removeAt(timeBufs.size - 1); xBufs.removeAt(xBufs.size - 1)
            yBufs.removeAt(yBufs.size - 1); zBufs.removeAt(zBufs.size - 1)
        }
    }

    private fun resetBuffers() { timeBufs.clear(); xBufs.clear(); yBufs.clear(); zBufs.clear() }

    companion object {
        fun appendKeyframe(data: JsonObject, time: Float, pos: Vec3) {
            if (!time.isFinite() || time < 0f || listOf(pos.x, pos.y, pos.z).any { !it.toFloat().isFinite() }) return
            val arr = data.get("keyframes").trackArray() ?: JsonArray().also { data.add("keyframes", it) }
            arr.add(JsonObject().apply {
                addProperty("time", time)
                add("position", JsonObject().apply {
                    addProperty("x", pos.x); addProperty("y", pos.y); addProperty("z", pos.z)
                })
            })
            val list = arr.toList().sortedBy { it.inspectorObject()?.get("time").inspectorFloat() }
            while (arr.size() > 0) arr.remove(0)
            list.forEach { arr.add(it) }
        }

        fun removeKeyframe(data: JsonObject, index: Int) {
            val arr = data.get("keyframes").trackArray() ?: return
            if (index in 0 until arr.size()) arr.remove(index)
        }
    }
}
