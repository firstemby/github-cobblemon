/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SplinePointTarget
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.world.phys.Vec3

class SplinePositionEditor {
    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        ImGui.pushID("shotPath/$shotId")
        try {
            val draft = Draft(scene, shotId, data)
            SplinePathEditor.render(draft.data, { SplinePointTarget(shotId, it) }, draft::commit)
        } finally {
            ImGui.popID()
        }
    }

    internal class Draft(private val scene: EditorScene, private val shotId: String, source: JsonObject) {
        val data: JsonObject = source.deepCopy()
        private var expected = source

        fun commit(after: JsonObject) {
            val current = scene.shots[shotId]?.positionData
            if (current !== expected || after == current) return
            scene.execute(ChangeShotChannel(shotId, ShotChannel.POSITION, expected, after))
            expected = scene.shots.getValue(shotId).positionData
            data.keySet().toList().forEach(data::remove)
            after.entrySet().forEach { (key, value) -> data.add(key, value.deepCopy()) }
        }
    }

    companion object {
        fun appendControlPoint(data: JsonObject, pos: Vec3) = SplinePathEditor.appendControlPoint(data, pos)
        fun removeControlPoint(data: JsonObject, index: Int) = SplinePathEditor.removeControlPoint(data, index)
    }
}
