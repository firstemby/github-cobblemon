/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class AddActor(private val actor: EditorActor) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.actors[actor.id] = actor }
    override fun undo(scene: EditorScene) { scene.actors.remove(actor.id) }
}

class RemoveActor(private val actorId: String, private val actor: EditorActor) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.actors.remove(actorId) }
    override fun undo(scene: EditorScene) { scene.actors[actorId] = actor }
}

class ChangeActorKind(
    private val actorId: String,
    private val oldKind: ActorKind,
    private val newKind: ActorKind
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        scene.actors[actorId]?.kind = newKind
    }
    override fun undo(scene: EditorScene) {
        scene.actors[actorId]?.kind = oldKind
    }
}
