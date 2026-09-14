/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.StartPoseMode
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.model.cinematic.ChangeSceneProperty
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActorDirection
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class BeatActorPanel {
    fun render(scene: EditorScene, beatId: String, selection: SceneSelectionManager) {
        val beat = scene.beats[beatId] ?: return
        if (scene.actors.isEmpty()) {
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.beat_actor.empty"))
            return
        }
        scene.actors.values.forEach { actor ->
            ImGui.pushID("beatActor/$beatId/${actor.id}")
            try {
                ImGui.separator()
                ImGui.textWrapped(actor.id)
                ImGui.text(I18n.get("cobblemon.cinematic_editor.beat_actor.visibility"))
                EditorWidgets.fullWidth()
                val visibility = beat.actorDirections[actor.id]?.visible
                if (ImGui.beginCombo("##visibility", visibilityLabel(visibility))) {
                    try {
                        for (value in listOf(null, true, false)) {
                            if (ImGui.selectable("${visibilityLabel(value)}###visibility$value", value == visibility)) {
                                changeDirection(scene, beat, actor.id, { it.visible }, { target, next -> target.visible = next }, value)
                            }
                        }
                    } finally { ImGui.endCombo() }
                }
                val direction = beat.actorDirections[actor.id]
                val popup = "##startPose"
                if (ImGui.button(if (direction?.startPose == null) I18n.get("cobblemon.cinematic_editor.beat_actor.set_pose") else I18n.get("cobblemon.cinematic_editor.beat_actor.edit_pose"), ImGui.getContentRegionAvailX(), 0f)) StartPosePopover.open(popup)
                StartPosePopover.render(popup, "$beatId/${actor.id}", scene, direction) { pose ->
                    changeDirection(scene, beat, actor.id, { it.startPose }, { target, next -> target.startPose = next }, pose)
                }
                ImGui.text(I18n.get("cobblemon.cinematic_editor.beat_actor.pose_mode"))
                EditorWidgets.fullWidth()
                val currentMode = beat.actorDirections[actor.id]?.startPoseMode
                if (ImGui.beginCombo("##mode", poseModeLabel(currentMode))) {
                    try {
                        for (mode in listOf<StartPoseMode?>(null) + StartPoseMode.entries) {
                            if (ImGui.selectable("${poseModeLabel(mode)}###mode${mode?.name}", mode == currentMode)) {
                                changeDirection(scene, beat, actor.id, { it.startPoseMode }, { target, next -> target.startPoseMode = next }, mode)
                            }
                        }
                    } finally { ImGui.endCombo() }
                }
                val current = beat.actorDirections[actor.id]
                if (ImGui.button(I18n.get("cobblemon.cinematic_editor.beat_actor.edit_tracks", current?.tracks?.size ?: 0), ImGui.getContentRegionAvailX(), 0f)) {
                    if (current == null) setDirection(scene, beat, actor.id, EditorActorDirection())
                    selection.select(SceneSelection.BeatActorSelected(beatId, actor.id))
                }
                if (beat.actorDirections.containsKey(actor.id) && ImGui.button(I18n.get("cobblemon.cinematic_editor.beat_actor.remove"))) {
                    setDirection(scene, beat, actor.id, null)
                }
            } finally { ImGui.popID() }
        }
    }

    private fun visibilityLabel(value: Boolean?): String = I18n.get(when (value) {
        true -> "cobblemon.cinematic_editor.beat_actor.visible"
        false -> "cobblemon.cinematic_editor.beat_actor.hidden"
        null -> "cobblemon.cinematic_editor.beat_actor.carry"
    })

    companion object {
        internal fun <T> changeDirection(
            scene: EditorScene, beat: EditorBeat, actorId: String,
            getter: (EditorActorDirection) -> T, setter: (EditorActorDirection, T) -> Unit, value: T
        ): Boolean {
            if (scene.beats[beat.id] !== beat) return false
            val current = beat.actorDirections[actorId]
            val target = current ?: EditorActorDirection()
            val previous = getter(target)
            if (previous == value) return false
            if (current == null) {
                setter(target, value)
                setDirection(scene, beat, actorId, target)
            } else {
                scene.execute(ChangeSceneProperty(target, setter, previous, value))
            }
            return true
        }

        private fun setDirection(scene: EditorScene, beat: EditorBeat, actorId: String, next: EditorActorDirection?) {
            scene.execute(ChangeSceneProperty<EditorActorDirection?, EditorBeat>(beat,
                { target, value -> if (value == null) target.actorDirections.remove(actorId) else target.actorDirections[actorId] = value },
                beat.actorDirections[actorId], next))
        }
    }

    private fun poseModeLabel(mode: StartPoseMode?): String = I18n.get(when (mode) {
        null -> "cobblemon.cinematic_editor.beat_actor.default"
        StartPoseMode.TELEPORT -> "cobblemon.cinematic_editor.beat_actor.teleport"
        StartPoseMode.BLEND -> "cobblemon.cinematic_editor.beat_actor.blend"
        StartPoseMode.EXPECT -> "cobblemon.cinematic_editor.beat_actor.expect"
        StartPoseMode.IGNORE -> "cobblemon.cinematic_editor.beat_actor.ignore"
    })

}
