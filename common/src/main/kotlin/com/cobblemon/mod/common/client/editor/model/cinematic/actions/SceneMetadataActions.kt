/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

class RenameScene(private val oldId: String, private val newId: String) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.id = newId }
    override fun undo(scene: EditorScene) { scene.id = oldId }
}

class ChangeStartBeat(private val oldStartBeatId: String, private val newStartBeatId: String) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.startBeatId = newStartBeatId }
    override fun undo(scene: EditorScene) { scene.startBeatId = oldStartBeatId }
}

class ChangeDamageImmunity(private val oldValue: Boolean, private val newValue: Boolean) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.damageImmunityDuringTakeover = newValue }
    override fun undo(scene: EditorScene) { scene.damageImmunityDuringTakeover = oldValue }
}

class ChangeDisconnectPolicy(
    private val oldValue: DisconnectPolicy,
    private val newValue: DisconnectPolicy
) : SceneEditorAction {
    override fun execute(scene: EditorScene) { scene.disconnectPolicy = newValue }
    override fun undo(scene: EditorScene) { scene.disconnectPolicy = oldValue }
}
