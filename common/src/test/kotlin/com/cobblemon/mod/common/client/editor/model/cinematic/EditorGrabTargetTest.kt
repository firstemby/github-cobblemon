/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EditorGrabTargetTest {

    @Test
    fun `actor spline drag preserves independent undo and redo snapshots`() {
        val scene = EditorScene.createDefault()
        scene.beats.getValue("main").actorDirections["hero"] = EditorActorDirection(tracks = mutableListOf(
            EditorTrack("spline_movement", splineJson(Triple(0.0, 64.0, 0.0), Triple(5.0, 64.0, 0.0)))
        ))
        val target = ActorSplinePointTarget("main", "hero", 0, 1)
        for (height in listOf(70.0, 80.0)) {
            val before = target.owningJson(scene)!!.deepCopy()
            target.isolate(scene)
            target.moveTo(scene, 5.0, height, 0.0)
            target.commit(scene, before)
        }
        scene.undo()
        assertEquals(70.0, target.position(scene)!!.y)
        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y)
        scene.redo()
        assertEquals(70.0, target.position(scene)!!.y)
        scene.redo()
        assertEquals(80.0, target.position(scene)!!.y)
    }

    private fun keyframesJson(vararg positions: Triple<Double, Double, Double>) = JsonObject().apply {
        addProperty("type", "keyframe")
        add("keyframes", JsonArray().apply {
            positions.forEachIndexed { i, (x, y, z) ->
                add(JsonObject().apply {
                    addProperty("time", i.toFloat())
                    add("position", JsonObject().apply {
                        addProperty("x", x); addProperty("y", y); addProperty("z", z)
                    })
                })
            }
        })
    }

    @Test
    fun `a camera keyframe drag commits one undoable move`() {
        val scene = EditorScene()
        scene.shots["cam"] = EditorShot(
            id = "cam",
            positionData = keyframesJson(Triple(0.0, 64.0, 0.0), Triple(5.0, 64.0, 0.0)),
            rotationData = JsonObject().apply {
                addProperty("type", "fixed"); addProperty("yaw", 0f); addProperty("pitch", 0f)
            }
        )

        val target = ShotKeyframeTarget("cam", row = 1)
        val before = target.owningJson(scene)!!.deepCopy()

        target.moveTo(scene, 5.0, 70.0, 2.0)
        target.commit(scene, before)

        assertEquals(70.0, target.position(scene)!!.y, 1e-6)
        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y, 1e-6)
    }

    @Test
    fun `an actor keyframe drag commits through the beat's track data`() {
        val scene = EditorScene()
        val beat = EditorBeat(id = "main")
        beat.actorDirections["hero"] = EditorActorDirection(
            tracks = mutableListOf(
                EditorTrack("keyframe_movement", keyframesJson(Triple(1.0, 64.0, 1.0)))
            )
        )
        scene.beats["main"] = beat

        val target = ActorKeyframeTarget("main", "hero", trackIndex = 0, row = 0)
        val before = target.owningJson(scene)!!.deepCopy()

        target.moveTo(scene, 3.0, 65.0, 1.0)
        target.commit(scene, before)

        assertEquals(3.0, target.position(scene)!!.x, 1e-6)
        scene.undo()
        assertEquals(1.0, target.position(scene)!!.x, 1e-6)
    }

    @Test
    fun `a second camera drag does not corrupt the first drag's redo state`() {
        val scene = EditorScene()
        scene.shots["cam"] = EditorShot(
            id = "cam",
            positionData = keyframesJson(Triple(0.0, 64.0, 0.0)),
            rotationData = JsonObject().apply {
                addProperty("type", "fixed"); addProperty("yaw", 0f); addProperty("pitch", 0f)
            }
        )
        val target = ShotKeyframeTarget("cam", row = 0)

        val before1 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 0.0, 70.0, 0.0)
        target.commit(scene, before1)

        val before2 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 0.0, 80.0, 0.0)
        target.commit(scene, before2)

        scene.undo()
        assertEquals(70.0, target.position(scene)!!.y, 1e-6)
        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y, 1e-6)
        scene.redo()
        assertEquals(70.0, target.position(scene)!!.y, 1e-6)
    }

    @Test
    fun `a second actor drag does not corrupt the first drag's redo state`() {
        val scene = EditorScene()
        val beat = EditorBeat(id = "main")
        beat.actorDirections["hero"] = EditorActorDirection(
            tracks = mutableListOf(
                EditorTrack("keyframe_movement", keyframesJson(Triple(1.0, 64.0, 1.0)))
            )
        )
        scene.beats["main"] = beat
        val target = ActorKeyframeTarget("main", "hero", trackIndex = 0, row = 0)

        val before1 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 2.0, 64.0, 1.0)
        target.commit(scene, before1)

        val before2 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 3.0, 64.0, 1.0)
        target.commit(scene, before2)

        scene.undo()
        assertEquals(2.0, target.position(scene)!!.x, 1e-6)
        scene.undo()
        assertEquals(1.0, target.position(scene)!!.x, 1e-6)
        scene.redo()
        assertEquals(2.0, target.position(scene)!!.x, 1e-6)
    }

    private fun splineJson(vararg points: Triple<Double, Double, Double>) = JsonObject().apply {
        addProperty("type", "spline")
        addProperty("duration", 5f)
        add("controlPoints", JsonArray().apply {
            points.forEach { (x, y, z) ->
                add(JsonObject().apply {
                    addProperty("x", x); addProperty("y", y); addProperty("z", z)
                })
            }
        })
    }

    @Test
    fun `a spline control point drag commits one undoable move`() {
        // Control points sit directly in controlPoints[i]; keyframes nest under [i].position.
        val scene = EditorScene()
        scene.shots["cam"] = EditorShot(
            id = "cam",
            positionData = splineJson(Triple(0.0, 64.0, 0.0), Triple(10.0, 64.0, 0.0)),
            rotationData = JsonObject().apply {
                addProperty("type", "fixed"); addProperty("yaw", 0f); addProperty("pitch", 0f)
            }
        )
        val target = SplinePointTarget("cam", index = 1)
        assertEquals(10.0, target.position(scene)!!.x, 1e-6)

        val before = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 12.0, 70.0, 3.0)
        target.commit(scene, before)

        assertEquals(70.0, target.position(scene)!!.y, 1e-6)
        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y, 1e-6)
        assertEquals(10.0, target.position(scene)!!.x, 1e-6)
    }

    @Test
    fun `an out-of-range row degrades instead of throwing`() {
        val scene = EditorScene()
        scene.shots["cam"] = EditorShot(
            id = "cam",
            positionData = keyframesJson(Triple(0.0, 64.0, 0.0)),
            rotationData = JsonObject().apply {
                addProperty("type", "fixed"); addProperty("yaw", 0f); addProperty("pitch", 0f)
            }
        )
        assertNull(ShotKeyframeTarget("cam", row = 7).position(scene))
        ShotKeyframeTarget("cam", row = 7).moveTo(scene, 1.0, 1.0, 1.0)
        assertEquals(64.0, ShotKeyframeTarget("cam", row = 0).position(scene)!!.y, 1e-6)
    }

    @Test
    fun `a drag after an undo does not corrupt the undone action's state`() {
        val scene = EditorScene()
        scene.shots["cam"] = EditorShot(
            id = "cam",
            positionData = keyframesJson(Triple(0.0, 64.0, 0.0)),
            rotationData = JsonObject().apply {
                addProperty("type", "fixed"); addProperty("yaw", 0f); addProperty("pitch", 0f)
            }
        )
        val target = ShotKeyframeTarget("cam", row = 0)

        val before1 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 0.0, 70.0, 0.0)
        target.commit(scene, before1)

        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y, 1e-6)

        target.isolate(scene)
        target.moveTo(scene, 0.0, 90.0, 0.0)

        scene.redo()
        assertEquals(70.0, target.position(scene)!!.y, 1e-6)

        // The corruption lands in the action's oldValue, so only a second undo can see it.
        scene.undo()
        assertEquals(64.0, target.position(scene)!!.y, 1e-6)
    }

    @Test
    fun `an actor drag after an undo does not corrupt the undone action's state`() {
        val scene = EditorScene()
        val beat = EditorBeat(id = "main")
        beat.actorDirections["hero"] = EditorActorDirection(
            tracks = mutableListOf(
                EditorTrack("keyframe_movement", keyframesJson(Triple(1.0, 64.0, 1.0)))
            )
        )
        scene.beats["main"] = beat
        val target = ActorKeyframeTarget("main", "hero", trackIndex = 0, row = 0)

        val before1 = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 2.0, 64.0, 1.0)
        target.commit(scene, before1)

        scene.undo()
        assertEquals(1.0, target.position(scene)!!.x, 1e-6)

        target.isolate(scene)
        target.moveTo(scene, 9.0, 64.0, 1.0)

        scene.redo()
        assertEquals(2.0, target.position(scene)!!.x, 1e-6)

        scene.undo()
        assertEquals(1.0, target.position(scene)!!.x, 1e-6)
    }
}
