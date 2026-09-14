/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.google.gson.JsonArray
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChangeShotChannelTest {

    private fun newScene(): EditorScene {
        val scene = EditorScene(id = "test:scene", startBeatId = "main")
        val pos = JsonObject().apply { addProperty("type", "fixed") }
        val rot = JsonObject().apply { addProperty("type", "fixed") }
        scene.shots["s1"] = EditorShot(id = "s1", positionData = pos, rotationData = rot)
        return scene
    }

    @Test
    fun `execute replaces channel JsonObject, undo restores old value`() {
        val scene = newScene()
        val oldPos = scene.shots["s1"]!!.positionData
        val newPos = JsonObject().apply {
            addProperty("type", "keyframe")
            add("keyframes", JsonArray())
        }

        val action = ChangeShotChannel(
            shotId = "s1",
            channel = ShotChannel.POSITION,
            oldValue = oldPos,
            newValue = newPos
        )

        scene.execute(action)
        assertEquals("keyframe", scene.shots["s1"]!!.positionData.get("type").asString)

        scene.undo()
        assertEquals("fixed", scene.shots["s1"]!!.positionData.get("type").asString)
    }

    @Test
    fun `modifier add-remove round trips through undo`() {
        val scene = newScene()
        val mod = JsonObject().apply {
            addProperty("type", "smoothing")
            addProperty("position", 0.3f)
        }

        scene.execute(ChangeShotModifier.add("s1", mod))
        assertEquals(1, scene.shots["s1"]!!.modifiers.size)

        scene.undo()
        assertEquals(0, scene.shots["s1"]!!.modifiers.size)
    }

    @Test fun `nested channel edits do not corrupt earlier undo and redo snapshots`() {
        val scene = newScene()
        val shot = scene.shots["s1"]!!
        val initial = shot.positionData.deepCopy()
        val replacement = JsonObject().apply { add("point", JsonObject().apply { addProperty("x", 1) }) }
        scene.execute(ChangeShotChannel("s1", ShotChannel.POSITION, initial, replacement))
        val before = shot.positionData.deepCopy()
        shot.positionData.getAsJsonObject("point").addProperty("x", 2)
        scene.execute(ChangeShotChannel("s1", ShotChannel.POSITION, before, shot.positionData))
        scene.undo()
        assertEquals(1, shot.positionData.getAsJsonObject("point").get("x").asInt)
        scene.undo()
        assertEquals(initial, shot.positionData)
        scene.redo()
        assertEquals(1, shot.positionData.getAsJsonObject("point").get("x").asInt)
        scene.redo()
        assertEquals(2, shot.positionData.getAsJsonObject("point").get("x").asInt)
    }

    @Test fun `undoing a middle modifier removal restores its position without replacing its neighbor`() {
        val scene = newScene()
        val modifiers = scene.shots["s1"]!!.modifiers
        val originals = (0..2).map { index -> JsonObject().apply { addProperty("id", index) } }
        modifiers.addAll(originals)
        scene.execute(ChangeShotModifier.remove("s1", 1, modifiers[1]))
        assertEquals(listOf(originals[0], originals[2]), modifiers)
        repeat(2) {
            scene.undo()
            assertEquals(originals, modifiers)
            scene.redo()
            assertEquals(listOf(originals[0], originals[2]), modifiers)
        }
    }

    @Test fun `modifier redo restores the captured nested values after later edits`() {
        val scene = newScene()
        val modifiers = scene.shots["s1"]!!.modifiers
        val modifier = JsonObject().apply { add("position", JsonObject().apply { addProperty("x", 1) }) }
        scene.execute(ChangeShotModifier.add("s1", modifier))
        val before = modifiers[0].deepCopy()
        modifiers[0].getAsJsonObject("position").addProperty("x", 2)
        scene.execute(ChangeShotModifier.edit("s1", 0, before, modifiers[0]))
        scene.undo()
        scene.undo()
        assertEquals(0, modifiers.size)
        scene.redo()
        assertEquals(1, modifiers[0].getAsJsonObject("position").get("x").asInt)
        scene.redo()
        assertEquals(2, modifiers[0].getAsJsonObject("position").get("x").asInt)
    }

}
