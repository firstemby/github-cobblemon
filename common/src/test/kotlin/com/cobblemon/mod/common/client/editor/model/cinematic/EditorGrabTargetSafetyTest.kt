package com.cobblemon.mod.common.client.editor.model.cinematic

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EditorGrabTargetSafetyTest {
    @Test
    fun `malformed keyframe rows degrade safely`() {
        val scene = EditorScene()
        scene.shots["shot"] = EditorShot("shot", JsonObject().apply {
            addProperty("type", "keyframe")
            addProperty("keyframes", "not an array")
        }, JsonObject())
        assertNull(ShotKeyframeTarget("shot", 0).position(scene))
    }

    @Test
    fun `invalid moves and unchanged commits create no history`() {
        val scene = sceneWithPoint()
        val target = ShotKeyframeTarget("shot", 0)
        val before = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, Double.NaN, 4.0, 5.0)
        target.commit(scene, before)
        assertEquals(1.0, target.position(scene)!!.x)
        assertFalse(scene.canUndo())
    }

    @Test
    fun `restore replaces the complete owner snapshot`() {
        val scene = sceneWithPoint()
        val target = ShotKeyframeTarget("shot", 0)
        val before = target.owningJson(scene)!!.deepCopy()
        target.moveTo(scene, 7.0, 8.0, 9.0)
        target.restore(scene, before)
        assertEquals(before, target.owningJson(scene))
    }

    private fun sceneWithPoint() = EditorScene().apply {
        shots["shot"] = EditorShot("shot", JsonObject().apply {
            addProperty("type", "keyframe")
            add("keyframes", JsonArray().apply {
                add(JsonObject().apply {
                    add("position", JsonObject().apply {
                        addProperty("x", 1.0); addProperty("y", 2.0); addProperty("z", 3.0)
                    })
                })
            })
        }, JsonObject())
    }
}
