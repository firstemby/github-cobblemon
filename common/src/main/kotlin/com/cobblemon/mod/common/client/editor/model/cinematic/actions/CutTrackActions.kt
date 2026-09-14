/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutEntry
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorCutTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction

private fun EditorScene.cutEntries(beatId: String, feedId: String): MutableList<EditorCutEntry>? =
    beats[beatId]?.cuts?.get(feedId)?.entries

class AddCutEntry(
    private val beatId: String,
    private val feedId: String,
    private val entry: EditorCutEntry
) : SceneEditorAction {
    private var createdTrack = false

    override fun execute(scene: EditorScene) {
        val beat = scene.beats[beatId] ?: return
        createdTrack = feedId !in beat.cuts
        val entries = beat.cuts.getOrPut(feedId) { EditorCutTrack() }.entries
        val index = entries.indexOfFirst { it.time > entry.time }.takeIf { it >= 0 } ?: entries.size
        entries.add(index, entry)
    }
    override fun undo(scene: EditorScene) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        val idx = list.indexOfLast { it === entry }
        if (idx >= 0) list.removeAt(idx)
        if (createdTrack && list.isEmpty()) scene.beats[beatId]?.cuts?.remove(feedId)
    }
}

class RemoveCutEntry(
    private val beatId: String,
    private val feedId: String,
    private val entryIndex: Int,
    private val existing: EditorCutEntry
) : SceneEditorAction {
    override fun execute(scene: EditorScene) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        if (entryIndex in list.indices) list.removeAt(entryIndex)
    }
    override fun undo(scene: EditorScene) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        val insertAt = entryIndex.coerceIn(0, list.size)
        list.add(insertAt, existing)
    }
}

class ChangeCutEntry private constructor(
    private val beatId: String,
    private val feedId: String,
    private val entryIndex: Int,
    private val apply: (EditorCutEntry, Boolean) -> Unit
) : SceneEditorAction {
    override fun execute(scene: EditorScene) = applyAt(scene, forward = true)
    override fun undo(scene: EditorScene) = applyAt(scene, forward = false)

    private fun applyAt(scene: EditorScene, forward: Boolean) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        val entry = list.getOrNull(entryIndex) ?: return
        apply(entry, forward)
    }

    companion object {
        fun time(beatId: String, feedId: String, entryIndex: Int, oldTime: Float, newTime: Float) =
            ChangeCutEntry(beatId, feedId, entryIndex) { entry, forward ->
                entry.time = if (forward) newTime else oldTime
            }

        fun shotId(beatId: String, feedId: String, entryIndex: Int, oldShotId: String, newShotId: String) =
            ChangeCutEntry(beatId, feedId, entryIndex) { entry, forward ->
                entry.shotId = if (forward) newShotId else oldShotId
            }

        fun transition(beatId: String, feedId: String, entryIndex: Int,
                       oldTransition: CutTransition, newTransition: CutTransition) =
            ChangeCutEntry(beatId, feedId, entryIndex) { entry, forward ->
                entry.transition = if (forward) newTransition else oldTransition
            }
    }
}

class SplitCutAt(
    private val beatId: String,
    private val feedId: String,
    private val playheadSeconds: Float
) : SceneEditorAction {
    private var inserted: EditorCutEntry? = null
    private var insertIndex: Int = -1

    override fun execute(scene: EditorScene) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        if (list.isEmpty()) return
        val owningIdx = list.indexOfLast { it.time <= playheadSeconds }
        if (owningIdx < 0) return
        if (list[owningIdx].time == playheadSeconds) return
        val owning = list[owningIdx]
        val dup = EditorCutEntry(time = playheadSeconds, shotId = owning.shotId, transition = owning.transition)
        val insertAt = owningIdx + 1
        list.add(insertAt, dup)
        inserted = dup
        insertIndex = insertAt
    }

    override fun undo(scene: EditorScene) {
        val list = scene.cutEntries(beatId, feedId) ?: return
        val dup = inserted ?: return
        if (insertIndex in list.indices && list[insertIndex] === dup) {
            list.removeAt(insertIndex)
        } else {
            val idx = list.indexOfLast { it === dup }
            if (idx >= 0) list.removeAt(idx)
        }
        inserted = null
        insertIndex = -1
    }
}
