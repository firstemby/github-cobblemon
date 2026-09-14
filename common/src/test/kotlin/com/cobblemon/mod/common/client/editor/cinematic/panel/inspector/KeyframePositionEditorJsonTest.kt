/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.KeyframePosition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeyframePositionEditorJsonTest {

    @Test
    fun `invalid additions never create or alter keyframes`() {
        val data = JsonObject()
        for (time in listOf(-1f, Float.NaN, Float.POSITIVE_INFINITY)) {
            KeyframePositionEditor.appendKeyframe(data, time, Vec3.ZERO, null)
            assertEquals(JsonObject(), data)
        }
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE)) {
            KeyframePositionEditor.appendKeyframe(data, 0f, Vec3(1.0, value, 2.0), null)
            assertEquals(JsonObject(), data)
        }
    }

    @Test
    fun `evaluation of malformed data and invalid times uses the safe fallback`() {
        for (value in listOf("null", "{}", "true", "\"v.keyframes\"", "[null]", "[{\"time\":0}]")) {
            val data = JsonObject().apply { add("keyframes", JsonParser.parseString(value)) }
            val before = data.deepCopy()
            assertEquals(Vec3.ZERO, KeyframePositionEditor.evaluateAt(data, 1f))
            assertEquals(before, data)
        }
        val data = JsonObject()
        KeyframePositionEditor.appendKeyframe(data, 0f, Vec3(1.0, 2.0, 3.0), null)
        assertEquals(Vec3.ZERO, KeyframePositionEditor.evaluateAt(data, Float.NaN))
        assertEquals(Vec3.ZERO, KeyframePositionEditor.evaluateAt(data, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `selection belongs to a scene and shot while pending focus survives a switch`() {
        val editor = KeyframePositionEditor()
        val first = EditorScene()
        val second = EditorScene()
        editor.setSelectedRow(2)
        editor.syncSelection(first, "shot", 3)
        assertEquals(2, editor.currentSelectionRow)
        editor.syncSelection(first, "other", 3)
        assertEquals(-1, editor.currentSelectionRow)
        editor.setSelectedRow(1)
        editor.syncSelection(second, "shot", 3)
        assertEquals(1, editor.currentSelectionRow)
        editor.syncSelection(first, "shot", 3)
        assertEquals(-1, editor.currentSelectionRow)
        editor.setSelectedRow(2)
        editor.syncSelection(first, "shot", 3)
        editor.syncSelection(first, "shot", 2)
        assertEquals(-1, editor.currentSelectionRow)
    }

    @Test
    fun `appendKeyframe writes JSON that KeyframePosition_fromJson accepts`() {
        val data = JsonObject().apply {
            addProperty("type", "keyframe")
            addProperty("easing", "linear")
            add("keyframes", JsonArray())
        }

        KeyframePositionEditor.appendKeyframe(data, time = 0.5f, pos = Vec3(1.0, 2.0, 3.0), easing = "ease_out")

        val parsed = KeyframePosition.fromJson(data)
        assertEquals(1, parsed.keyframes.size)
        assertEquals(0.5f, parsed.keyframes[0].time)
        assertEquals(Vec3(1.0, 2.0, 3.0), parsed.keyframes[0].position)
        assertEquals("ease_out", parsed.keyframes[0].easing)
    }

    @Test
    fun `removeKeyframe drops the target row and keeps ordering`() {
        val data = JsonObject().apply {
            addProperty("type", "keyframe")
            addProperty("easing", "linear")
            add("keyframes", JsonArray())
        }
        KeyframePositionEditor.appendKeyframe(data, 0.0f, Vec3(0.0, 0.0, 0.0), null)
        KeyframePositionEditor.appendKeyframe(data, 1.0f, Vec3(5.0, 0.0, 0.0), null)
        KeyframePositionEditor.appendKeyframe(data, 2.0f, Vec3(10.0, 0.0, 0.0), null)

        val scene = EditorScene().apply { shots["shot"] = EditorShot("shot", data, JsonObject()) }
        val draft = KeyframeChannelDraft(scene, "shot", ShotChannel.POSITION, data)
        draft.remove(1)

        val parsed = KeyframePosition.fromJson(draft.data)
        assertEquals(2, parsed.keyframes.size)
        assertEquals(0f, parsed.keyframes[0].time)
        assertEquals(2f, parsed.keyframes[1].time)
        scene.undo()
        assertEquals(data, scene.shots.getValue("shot").positionData)
    }

    @Test
    fun `evaluateAt uses KeyframePosition semantics when 2+ keyframes exist`() {
        val data = JsonObject().apply {
            addProperty("type", "keyframe")
            addProperty("easing", "linear")
            add("keyframes", JsonArray())
        }
        KeyframePositionEditor.appendKeyframe(data, 0.0f, Vec3(0.0, 0.0, 0.0), null)
        KeyframePositionEditor.appendKeyframe(data, 2.0f, Vec3(2.0, 0.0, 0.0), null)

        val mid = KeyframePositionEditor.evaluateAt(data, 1.0f)
        assertEquals(1.0, mid.x, 1e-6)
    }
}
