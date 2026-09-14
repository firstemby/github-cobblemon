/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorLens
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class AddShot(private val shot: EditorShot) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.shots[shot.id] = shot }
    override fun undo(scene: EditorScene) { scene.shots.remove(shot.id) }
}

class RemoveShot(private val shotId: String, private val shot: EditorShot) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.shots.remove(shotId) }
    override fun undo(scene: EditorScene) { scene.shots[shotId] = shot }
}

class SetShotLens(
    private val shotId: String,
    private val oldLens: EditorLens?,
    private val newLens: EditorLens?
) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.shots[shotId]?.lens = newLens }
    override fun undo(scene: EditorScene) { scene.shots[shotId]?.lens = oldLens }
}

class ChangeLensAspectCrop(
    private val shotId: String,
    private val oldValue: Float?,
    private val newValue: Float?
) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.shots[shotId]?.lens?.aspectCrop = newValue }
    override fun undo(scene: EditorScene) { scene.shots[shotId]?.lens?.aspectCrop = oldValue }
}

class ChangeLensLetterbox(
    private val shotId: String,
    private val oldValue: Float,
    private val newValue: Float
) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.shots[shotId]?.lens?.letterbox = newValue }
    override fun undo(scene: EditorScene) { scene.shots[shotId]?.lens?.letterbox = oldValue }
}
