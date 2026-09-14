/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import net.minecraft.client.resources.language.I18n

sealed interface SceneSelection {
    data object SceneRoot : SceneSelection
    data class ActorSelected(val actorId: String) : SceneSelection
    data class ShotSelected(val shotId: String) : SceneSelection
    data class FeedSelected(val index: Int) : SceneSelection
    data class BeatSelected(val beatId: String) : SceneSelection
    data class TransitionSelected(val index: Int) : SceneSelection
    data class ParameterSelected(val name: String) : SceneSelection
    data class CutSelected(val beatId: String, val feedId: String, val entryIndex: Int) : SceneSelection
    data class BeatActorSelected(val beatId: String, val actorId: String) : SceneSelection
}

class SceneSelectionManager {
    private var activeBeatId: String? = null
    var selection: SceneSelection = SceneSelection.SceneRoot
        private set

    fun select(newSelection: SceneSelection) {
        when (newSelection) {
            is SceneSelection.BeatSelected -> activeBeatId = newSelection.beatId
            is SceneSelection.BeatActorSelected -> activeBeatId = newSelection.beatId
            is SceneSelection.CutSelected -> activeBeatId = newSelection.beatId
            else -> Unit
        }
        selection = newSelection
    }

    fun clear() {
        activeBeatId = null
        select(SceneSelection.SceneRoot)
    }

    fun getSelectedActorId(): String? = (selection as? SceneSelection.ActorSelected)?.actorId

    fun getSelectedShotId(): String? = (selection as? SceneSelection.ShotSelected)?.shotId

    fun getSelectedBeatId(): String? = activeBeatId

    fun getSelectedCut(): SceneSelection.CutSelected? = selection as? SceneSelection.CutSelected

    fun getSelectedBeatActor(): SceneSelection.BeatActorSelected? = selection as? SceneSelection.BeatActorSelected

    fun onFeedRemoved(index: Int) {
        val selected = selection as? SceneSelection.FeedSelected ?: return
        when {
            selected.index == index -> clear()
            selected.index > index -> select(SceneSelection.FeedSelected(selected.index - 1))
        }
    }

    fun onTransitionRemoved(index: Int) {
        val selected = selection as? SceneSelection.TransitionSelected ?: return
        when {
            selected.index == index -> clear()
            selected.index > index -> select(SceneSelection.TransitionSelected(selected.index - 1))
        }
    }

    fun describeSelection(): String = when (val s = selection) {
        is SceneSelection.SceneRoot -> I18n.get("cobblemon.cinematic_editor.selection.scene")
        is SceneSelection.ActorSelected -> I18n.get("cobblemon.cinematic_editor.selection.actor", s.actorId)
        is SceneSelection.ShotSelected -> I18n.get("cobblemon.cinematic_editor.selection.shot", s.shotId)
        is SceneSelection.FeedSelected -> I18n.get("cobblemon.cinematic_editor.selection.feed", s.index)
        is SceneSelection.BeatSelected -> I18n.get("cobblemon.cinematic_editor.selection.beat", s.beatId)
        is SceneSelection.TransitionSelected -> I18n.get("cobblemon.cinematic_editor.selection.transition", s.index)
        is SceneSelection.ParameterSelected -> I18n.get("cobblemon.cinematic_editor.selection.parameter", s.name)
        is SceneSelection.CutSelected -> I18n.get("cobblemon.cinematic_editor.selection.cut", s.feedId, s.entryIndex)
        is SceneSelection.BeatActorSelected -> I18n.get("cobblemon.cinematic_editor.selection.beat_actor", s.beatId, s.actorId)
    }

    private var pendingKeyframeRow: Int = -1
    fun setPendingKeyframeRowFocus(row: Int) { pendingKeyframeRow = row }
    fun consumePendingKeyframeRowFocus(): Int {
        val r = pendingKeyframeRow; pendingKeyframeRow = -1; return r
    }
}
