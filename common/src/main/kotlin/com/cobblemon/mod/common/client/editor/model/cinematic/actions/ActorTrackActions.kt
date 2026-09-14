/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction
import com.google.gson.JsonObject

private fun EditorScene.actorTracks(beatId: String, actorId: String): MutableList<EditorTrack>? =
    beats[beatId]?.actorDirections?.get(actorId)?.tracks

class AddActorTrack(
    private val beatId: String,
    private val actorId: String,
    private val track: EditorTrack
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        scene.actorTracks(beatId, actorId)?.add(track)
    }
    override fun undo(scene: EditorScene) {
        val list = scene.actorTracks(beatId, actorId) ?: return
        val idx = list.indexOfLast { it === track }
        if (idx >= 0) list.removeAt(idx)
    }
}

class RemoveActorTrack(
    private val beatId: String,
    private val actorId: String,
    private val index: Int,
    private val existing: EditorTrack
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        val list = scene.actorTracks(beatId, actorId) ?: return
        if (index in list.indices) list.removeAt(index)
    }
    override fun undo(scene: EditorScene) {
        val list = scene.actorTracks(beatId, actorId) ?: return
        val insertAt = index.coerceIn(0, list.size)
        list.add(insertAt, existing)
    }
}

// Deep-copy action snapshots so editing the live track cannot corrupt undo history.
class ChangeActorTrackData(
    private val beatId: String,
    private val actorId: String,
    private val index: Int,
    oldValue: JsonObject,
    newValue: JsonObject
) : SceneEditorAction {
    private val oldSnapshot: JsonObject = oldValue.deepCopy()
    private val newSnapshot: JsonObject = newValue.deepCopy()

    override fun execute(scene: EditorScene) = applyAt(scene, newSnapshot)
    override fun undo(scene: EditorScene) = applyAt(scene, oldSnapshot)

    private fun applyAt(scene: EditorScene, value: JsonObject) {
        val list = scene.actorTracks(beatId, actorId) ?: return
        val track = list.getOrNull(index) ?: return
        val current = track.data
        val keysToRemove = current.keySet().toList()
        for (k in keysToRemove) current.remove(k)
        for ((k, v) in value.entrySet()) current.add(k, v.deepCopy())
    }
}
