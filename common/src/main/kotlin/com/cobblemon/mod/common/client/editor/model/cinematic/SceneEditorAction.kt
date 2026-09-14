/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

interface SceneEditorAction {
    fun execute(scene: EditorScene)
    fun undo(scene: EditorScene)
}

class CompoundSceneAction(private val actions: List<SceneEditorAction>) : SceneEditorAction {
    override fun execute(scene: EditorScene) = actions.forEach { it.execute(scene) }
    override fun undo(scene: EditorScene) = actions.reversed().forEach { it.undo(scene) }
}

/** Targets are captured by reference; group target deletion with property edits to preserve undo ordering. */
class ChangeSceneProperty<T, S : Any>(
    private val target: S,
    private val setter: (S, T) -> Unit,
    private val oldValue: T,
    private val newValue: T
) : SceneEditorAction {
    override fun execute(scene: EditorScene) { setter(target, newValue) }
    override fun undo(scene: EditorScene) { setter(target, oldValue) }
}
