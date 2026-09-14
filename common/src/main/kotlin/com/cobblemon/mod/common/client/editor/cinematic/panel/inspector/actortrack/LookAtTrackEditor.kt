/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SetFromCameraButton
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inputFiniteFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorFloat
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorObject
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat

class LookAtTrackEditor {
    private val xBuf = ImFloat(); private val yBuf = ImFloat(); private val zBuf = ImFloat()
    private var syncedData: JsonObject? = null
    private var syncedGeneration = -1

    fun render(scene: EditorScene, beatId: String, actorId: String, trackIndex: Int, track: EditorTrack) {
        val data = track.data
        val target = data.get("target").inspectorObject()
        if (data !== syncedData || scene.editGeneration != syncedGeneration) {
            xBuf.set(target?.get("x").inspectorFloat(0f))
            yBuf.set(target?.get("y").inspectorFloat(64f))
            zBuf.set(target?.get("z").inspectorFloat(0f))
            syncedData = data
            syncedGeneration = scene.editGeneration
        }

        val id = "${beatId}_${actorId}_${trackIndex}"
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor_track.target"))
        ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##latX_$id", xBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "target", mapOf("x" to xBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##latY_$id", yBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "target", mapOf("y" to yBuf.get().toDouble()))
        ImGui.sameLine(); ImGui.setNextItemWidth(60f); if (inputFiniteFloat("##latZ_$id", zBuf)) editActorTrackVector(scene, beatId, actorId, trackIndex, track, "target", mapOf("z" to zBuf.get().toDouble()))
        ImGui.sameLine()
        SetFromCameraButton.render("lat_cam_$id") { cam ->
            if (editActorTrackVector(scene, beatId, actorId, trackIndex, track, "target",
                    mapOf("x" to cam.x, "y" to cam.y, "z" to cam.z))) {
                xBuf.set(cam.x.toFloat()); yBuf.set(cam.y.toFloat()); zBuf.set(cam.z.toFloat())
            }
        }
    }

}
