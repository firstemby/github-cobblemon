package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.camera.model.CoordinateValue
import com.cobblemon.mod.common.api.camera.model.PositionExpression
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorStartPose
import com.cobblemon.mod.common.util.asExpressionLike
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActorDirectionEditingTest {
    @Test
    fun `creating direction and setting its property is one undo operation`() {
        val scene = EditorScene.createDefault()
        val beat = scene.beats.getValue("main")
        assertFalse(BeatActorPanel.changeDirection(scene, beat, "hero", { it.visible }, { target, value -> target.visible = value }, null))
        assertTrue(beat.actorDirections.isEmpty())
        assertFalse(scene.canUndo())
        assertTrue(BeatActorPanel.changeDirection(scene, beat, "hero", { it.visible }, { target, value -> target.visible = value }, false))
        assertEquals(false, beat.actorDirections.getValue("hero").visible)
        assertFalse(BeatActorPanel.changeDirection(scene, beat, "hero", { it.visible }, { target, value -> target.visible = value }, false))
        assertTrue(scene.undo())
        assertTrue(beat.actorDirections.isEmpty())
        assertFalse(scene.canUndo())
        assertTrue(scene.redo())
        assertEquals(false, beat.actorDirections.getValue("hero").visible)
    }

    @Test
    fun `rotation edits preserve expressions and unedited coordinate precision`() {
        val expression = CoordinateValue.Dynamic("v.arena_center_x".asExpressionLike())
        val original = EditorStartPose(PositionExpression(expression, CoordinateValue.Static(64.123456789), CoordinateValue.Static(8.0)), CameraRotation(0f, 0f))
        val rotated = requireNotNull(StartPosePopover.createEditedPose(original, emptyMap(), 90f, 10f, 0.5f))
        assertSame(expression, rotated.position.x)
        assertSame(original.position.y, rotated.position.y)
        assertEquals(CameraRotation(90f, 10f), rotated.rotation)
        assertEquals(CameraRotation(0f, 0f), original.rotation)
        val moved = requireNotNull(StartPosePopover.createEditedPose(rotated, mapOf("z" to 12f), 90f, 10f, 0.5f))
        assertSame(expression, moved.position.x)
        assertSame(original.position.y, moved.position.y)
        assertEquals(CoordinateValue.Static(12.0), moved.position.z)
        assertSame(moved, StartPosePopover.createEditedPose(moved, emptyMap(), 90f, 10f, 0.5f))
        assertNull(StartPosePopover.createEditedPose(moved, mapOf("x" to Float.NaN), 90f, 10f, 0.5f))
    }
}
