/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.camera.model.ParameterDefinition
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import java.nio.file.Path

class EditorScene(
    var id: String = "new_scene",
    var startBeatId: String = "main",
    val parameters: MutableMap<String, ParameterDefinition> = mutableMapOf(),
    val actors: MutableMap<String, EditorActor> = mutableMapOf(),
    val shots: MutableMap<String, EditorShot> = mutableMapOf(),
    val feeds: MutableList<EditorFeed> = mutableListOf(),
    val beats: MutableMap<String, EditorBeat> = mutableMapOf(),
    val transitions: MutableList<EditorBeatTransition> = mutableListOf(),
    var sourcePath: Path? = null,
    val version: Int = 2,
    var damageImmunityDuringTakeover: Boolean = false,
    var disconnectPolicy: DisconnectPolicy = DisconnectPolicy.END_SCENE
) {
    var isDirty: Boolean = false
        private set
    var editGeneration: Int = 0
        private set
    private val undoStack = mutableListOf<SceneEditorAction>()
    private val redoStack = mutableListOf<SceneEditorAction>()
    fun markDirty() { isDirty = true; editGeneration++ }
    fun clearDirty() { isDirty = false }
    fun execute(action: SceneEditorAction) { action.execute(this); undoStack.add(action); redoStack.clear(); markDirty() }
    fun undo(): Boolean {
        val action = undoStack.lastOrNull() ?: return false
        action.undo(this)
        undoStack.removeLast()
        redoStack.add(action)
        markDirty()
        return true
    }
    fun redo(): Boolean {
        val action = redoStack.lastOrNull() ?: return false
        action.execute(this)
        redoStack.removeLast()
        undoStack.add(action)
        markDirty()
        return true
    }
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    companion object { fun createDefault(): EditorScene = EditorScene().also { it.beats["main"] = EditorBeat(id = "main") } }
}
