/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class AddBeat(private val beat: EditorBeat) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.beats[beat.id] = beat }
    override fun undo(scene: EditorScene) { scene.beats.remove(beat.id) }
}

class RemoveBeat(private val beatId: String, private val beat: EditorBeat) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.beats.remove(beatId) }
    override fun undo(scene: EditorScene) { scene.beats[beatId] = beat }
}

class ChangeBeatProperty<T>(
    private val beatId: String,
    private val setter: (EditorBeat, T) -> Unit,
    private val oldValue: T,
    private val newValue: T
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        scene.beats[beatId]?.let { setter(it, newValue) }
    }
    override fun undo(scene: EditorScene) {
        scene.beats[beatId]?.let { setter(it, oldValue) }
    }
}
