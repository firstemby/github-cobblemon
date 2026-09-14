/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframeRotation
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyframeRotationEditorJsonTest {

    @Test
    fun `invalid additions leave the source untouched`() {
        val data = JsonObject()
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            KeyframeRotationEditor.appendKeyframe(data, value, 0f, 0f)
            KeyframeRotationEditor.appendKeyframe(data, 0f, value, 0f)
            KeyframeRotationEditor.appendKeyframe(data, 0f, 0f, value)
            assertEquals(JsonObject(), data)
        }
        KeyframeRotationEditor.appendKeyframe(data, -1f, 0f, 0f)
        assertEquals(JsonObject(), data)
    }

    private fun emptyData(): JsonObject = JsonObject().apply {
        addProperty("type", "keyframe")
        add("keyframes", JsonArray())
    }

    @Test
    fun `appendKeyframe writes JSON that KeyframeRotation_fromJson accepts`() {
        val data = emptyData()
        KeyframeRotationEditor.appendKeyframe(data, time = 0f, yaw = 0f, pitch = 0f)
        KeyframeRotationEditor.appendKeyframe(data, time = 1f, yaw = 90f, pitch = -10f)

        val parsed = KeyframeRotation.fromJson(data)
        assertEquals(2, parsed.keyframes.size)
        assertEquals(1f, parsed.keyframes[1].time)
        assertEquals(90f, parsed.keyframes[1].yaw)
        assertEquals(-10f, parsed.keyframes[1].pitch)
    }

    @Test
    fun `removeKeyframe drops the target row`() {
        val data = emptyData()
        KeyframeRotationEditor.appendKeyframe(data, 0f, 0f, 0f)
        KeyframeRotationEditor.appendKeyframe(data, 1f, 45f, 0f)
        KeyframeRotationEditor.appendKeyframe(data, 2f, 90f, 0f)

        val scene = EditorScene().apply { shots["shot"] = EditorShot("shot", JsonObject(), data) }
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.ROTATION, data)
        draft.remove(1)

        val parsed = KeyframeRotation.fromJson(draft.data)
        assertEquals(2, parsed.keyframes.size)
        assertEquals(0f, parsed.keyframes[0].yaw)
        assertEquals(90f, parsed.keyframes[1].yaw)
        scene.undo()
        assertEquals(data, scene.shots.getValue("shot").rotationData)
    }
}
