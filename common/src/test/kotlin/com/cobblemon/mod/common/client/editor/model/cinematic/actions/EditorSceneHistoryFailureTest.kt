package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EditorSceneHistoryFailureTest {
    @Test
    fun `a failed undo retains its history entry`() {
        val scene = EditorScene()
        scene.execute(object : SceneEditorAction {
            override fun execute(scene: EditorScene) = Unit
            override fun undo(scene: EditorScene) = error("failed")
        })
        assertThrows(IllegalStateException::class.java) { scene.undo() }
        assertTrue(scene.canUndo())
        assertFalse(scene.canRedo())
    }

    @Test
    fun `a failed redo retains its history entry`() {
        val scene = EditorScene()
        var failRedo = false
        scene.execute(object : SceneEditorAction {
            override fun execute(scene: EditorScene) { if (failRedo) error("failed") }
            override fun undo(scene: EditorScene) = Unit
        })
        scene.undo()
        failRedo = true
        assertThrows(IllegalStateException::class.java) { scene.redo() }
        assertFalse(scene.canUndo())
        assertTrue(scene.canRedo())
    }
}
