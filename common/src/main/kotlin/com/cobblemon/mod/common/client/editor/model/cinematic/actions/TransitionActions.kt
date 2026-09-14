/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeatTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class AddTransition(private val transition: EditorBeatTransition) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.transitions.add(transition) }
    override fun undo(scene: EditorScene) {
        val idx = scene.transitions.indexOf(transition)
        if (idx >= 0) scene.transitions.removeAt(idx)
    }
}

class RemoveTransition(
    private val index: Int,
    private val transition: EditorBeatTransition
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        if (index >= 0 && index < scene.transitions.size) scene.transitions.removeAt(index)
    }
    override fun undo(scene: EditorScene) {
        scene.transitions.add(index.coerceIn(0, scene.transitions.size), transition)
    }
}

class EditTransitionCondition(
    private val index: Int,
    private val oldCondition: String,
    private val newCondition: String
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        scene.transitions.getOrNull(index)?.condition = newCondition
    }
    override fun undo(scene: EditorScene) {
        scene.transitions.getOrNull(index)?.condition = oldCondition
    }
}
