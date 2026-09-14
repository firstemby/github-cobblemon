/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel

import com.cobblemon.mod.common.client.editor.cinematic.EditorLayoutBuilder
import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.api.cinematic.model.CutTransition
import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.api.cinematic.model.FeedKind
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ShotSourceTypes
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.SourceChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeatTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddActor
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.AddTransition
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveActor
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveFeed
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveShot
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RemoveTransition
import imgui.ImGui
import imgui.type.ImString
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics

class ScenePanel {

    private val newActorIdBuf = ImString(64)
    private val newShotIdBuf = ImString(64)
    private val newFeedIdBuf = ImString(64)
    private val newBeatIdBuf = ImString(64)
    private val newTransitionSourceBuf = ImString(64)
    private val newTransitionTargetBuf = ImString(64)

    fun render(scene: EditorScene, selection: SceneSelectionManager) {
        val open = ImGui.begin(I18n.get("cobblemon.cinematic_editor.scene_tree.title") + EditorLayoutBuilder.WINDOW_SCENE)
        try {
            if (!open) return
            EditorDiagnostics.panel("Scene")
            if (ImGui.selectable(I18n.get("cobblemon.cinematic_editor.scene_tree.settings"), selection.selection == SceneSelection.SceneRoot)) selection.select(SceneSelection.SceneRoot)
            ImGui.separator()

            if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.scene_tree.actors", scene.actors.size) + "###actors")) {
                renderActors(scene, selection)
            }
            if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.scene_tree.shots", scene.shots.size) + "###shots")) {
                renderShots(scene, selection)
            }
            if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.scene_tree.feeds", scene.feeds.size) + "###feeds")) {
                renderFeeds(scene, selection)
            }
            if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.scene_tree.beats", scene.beats.size) + "###beats")) {
                renderBeats(scene, selection)
            }
            if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.scene_tree.transitions", scene.transitions.size) + "###transitions")) {
                renderTransitions(scene, selection)
            }
        } finally {
            ImGui.end()
        }
    }

    private fun renderActors(scene: EditorScene, selection: SceneSelectionManager) {
        if (EditorWidgets.createRow("newActor", newActorIdBuf, I18n.get("cobblemon.cinematic_editor.scene_tree.add_actor"))) {
            val id = newActorIdBuf.get().trim()
            if (id.isNotEmpty() && id !in scene.actors) {
                scene.execute(AddActor(EditorActor(id = id, kind = ActorKind.RealEntity)))
                newActorIdBuf.set("")
                selection.select(SceneSelection.ActorSelected(id))
            }
        }

        scene.actors.entries.toList().forEach { (id, actor) ->
            val isSelected = selection.getSelectedActorId() == id
            val label = "$id  [${actorKindLabel(actor.kind)}]"
            EditorWidgets.objectRow("actor_$id", label, isSelected, {
                selection.select(SceneSelection.ActorSelected(id))
            }, {
                scene.execute(RemoveActor(id, actor))
                if (isSelected) selection.clear()
            })
        }
    }

    private fun renderShots(scene: EditorScene, selection: SceneSelectionManager) {
        if (EditorWidgets.createRow("newShot", newShotIdBuf, I18n.get("cobblemon.cinematic_editor.scene_tree.add_shot"))) {
            val id = newShotIdBuf.get().trim()
            if (id.isNotEmpty() && id !in scene.shots) {
                scene.execute(AddShot(EditorShot(
                    id = id,
                    positionData = ShotSourceTypes.defaultJsonFor(SourceChannel.POSITION, "fixed"),
                    rotationData = ShotSourceTypes.defaultJsonFor(SourceChannel.ROTATION, "fixed")
                )))
                newShotIdBuf.set("")
                selection.select(SceneSelection.ShotSelected(id))
            }
        }

        scene.shots.entries.toList().forEach { (id, shot) ->
            val isSelected = selection.getSelectedShotId() == id
            EditorWidgets.objectRow("shot_$id", id, isSelected, {
                selection.select(SceneSelection.ShotSelected(id))
            }, {
                scene.execute(RemoveShot(id, shot))
                if (isSelected) selection.clear()
            })
        }
    }

    private fun renderFeeds(scene: EditorScene, selection: SceneSelectionManager) {
        if (EditorWidgets.createRow("newFeed", newFeedIdBuf, I18n.get("cobblemon.cinematic_editor.scene_tree.add_feed"))) {
            val id = newFeedIdBuf.get().trim()
            if (id.isNotEmpty() && scene.feeds.none { it.id == id }) {
                scene.execute(AddFeed(EditorFeed(id = id, kind = FeedKind.Fullscreen)))
                newFeedIdBuf.set("")
                selection.select(SceneSelection.FeedSelected(scene.feeds.lastIndex))
            }
        }

        var feedRemoved = false
        scene.feeds.toList().forEachIndexed { index, feed ->
            if (feedRemoved) return@forEachIndexed
            val isSelected = (selection.selection as? SceneSelection.FeedSelected)?.index == index
            val label = "${feed.id}  [${feedKindLabel(feed.kind)}]"
            EditorWidgets.objectRow("feed_$index", label, isSelected, {
                selection.select(SceneSelection.FeedSelected(index))
            }, {
                scene.execute(RemoveFeed(index, feed))
                selection.onFeedRemoved(index)
                feedRemoved = true
            })
        }
    }

    private fun renderBeats(scene: EditorScene, selection: SceneSelectionManager) {
        if (EditorWidgets.createRow("newBeat", newBeatIdBuf, I18n.get("cobblemon.cinematic_editor.scene_tree.add_beat"))) {
            val id = newBeatIdBuf.get().trim()
            if (id.isNotEmpty() && id !in scene.beats) {
                scene.execute(AddBeat(EditorBeat(id = id)))
                newBeatIdBuf.set("")
                selection.select(SceneSelection.BeatSelected(id))
            }
        }

        scene.beats.entries.toList().forEach { (id, beat) ->
            val isSelected = selection.getSelectedBeatId() == id
            val isStart = scene.startBeatId == id
            val prefix = if (isStart) "> " else "  "
            EditorWidgets.objectRow("beat_$id", "$prefix$id", isSelected, {
                selection.select(SceneSelection.BeatSelected(id))
            }, {
                scene.execute(RemoveBeat(id, beat))
                if (isSelected) selection.clear()
            })
        }
    }

    private fun renderTransitions(scene: EditorScene, selection: SceneSelectionManager) {
        EditorWidgets.fullWidth()
        ImGui.inputTextWithHint("##trFrom", I18n.get("cobblemon.cinematic_editor.scene_tree.from_beat"), newTransitionSourceBuf)
        EditorWidgets.fullWidth()
        ImGui.inputTextWithHint("##trTo", I18n.get("cobblemon.cinematic_editor.scene_tree.to_beat"), newTransitionTargetBuf)
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.scene_tree.add_transition"), ImGui.getContentRegionAvailX(), 0f)) {
            val from = newTransitionSourceBuf.get().trim()
            val to = newTransitionTargetBuf.get().trim()
            if (from.isNotEmpty() && to.isNotEmpty() && from in scene.beats && to in scene.beats) {
                scene.execute(AddTransition(EditorBeatTransition(sourceBeatId = from, targetBeatId = to)))
                newTransitionSourceBuf.set("")
                newTransitionTargetBuf.set("")
                selection.select(SceneSelection.TransitionSelected(scene.transitions.lastIndex))
            }
        }

        var transitionRemoved = false
        scene.transitions.toList().forEachIndexed { index, tr ->
            if (transitionRemoved) return@forEachIndexed
            val isSelected = (selection.selection as? SceneSelection.TransitionSelected)?.index == index
            val label = "${tr.sourceBeatId} → ${tr.targetBeatId}  [${transitionKindLabel(tr)}]"
            EditorWidgets.objectRow("transition_$index", label, isSelected, {
                selection.select(SceneSelection.TransitionSelected(index))
            }, {
                scene.execute(RemoveTransition(index, tr))
                selection.onTransitionRemoved(index)
                transitionRemoved = true
            })
        }
    }

    private fun actorKindLabel(kind: ActorKind): String = when (kind) {
        ActorKind.PlayerTakeover -> I18n.get("cobblemon.cinematic_editor.actor.kind.player")
        ActorKind.RealEntity -> I18n.get("cobblemon.cinematic_editor.actor.kind.entity")
        ActorKind.SynthesizedPokemon -> I18n.get("cobblemon.cinematic_editor.actor.kind.pokemon")
        ActorKind.SynthesizedBedrock -> I18n.get("cobblemon.cinematic_editor.actor.kind.bedrock")
    }

    private fun feedKindLabel(kind: FeedKind): String = when (kind) {
        FeedKind.Fullscreen -> I18n.get("cobblemon.cinematic_editor.feed.kind.fullscreen")
        is FeedKind.Pip -> I18n.get("cobblemon.cinematic_editor.feed.kind.pip")
        is FeedKind.Split -> I18n.get("cobblemon.cinematic_editor.feed.kind.split")
        is FeedKind.Diagonal -> I18n.get("cobblemon.cinematic_editor.feed.kind.diagonal")
    }

    private fun transitionKindLabel(tr: EditorBeatTransition): String = when (tr.transition) {
        is CutTransition.Instant -> I18n.get("cobblemon.cinematic_editor.timeline.instant")
        is CutTransition.Blend -> I18n.get("cobblemon.cinematic_editor.timeline.blend")
    }
}
