/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel

import com.cobblemon.mod.common.api.camera.model.ParameterType
import com.cobblemon.mod.common.client.editor.cinematic.EditorLayoutBuilder
import net.minecraft.client.resources.language.I18n
import com.cobblemon.mod.common.api.cinematic.model.DisconnectPolicy
import com.cobblemon.mod.common.client.editor.cinematic.EditorDiagnostics
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelection
import com.cobblemon.mod.common.client.editor.cinematic.SceneSelectionManager
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorInspector
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ActorTracksEditor
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.BeatActorPanel
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.CueInspector
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.CutInspector
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.FeedInspector
import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.ShotInspector
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeBeatProperty
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeDamageImmunity
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeDisconnectPolicy
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeStartBeat
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.EditTransitionCondition
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.RenameScene
import imgui.ImGui
import imgui.flag.ImGuiWindowFlags
import imgui.type.ImBoolean
import imgui.type.ImFloat
import imgui.type.ImString

class SceneInspectorPanel {

    private val sceneIdBuf = ImString(64)
    private val startBeatBuf = ImString(64)
    private val transitionConditionBuf = ImString(256)
    private val beatSpeedBuf = ImFloat(1.0f)
    private val beatDurationBuf = ImFloat(0.0f)
    private var lastSelection: SceneSelection? = null
    private var lastScene: EditorScene? = null
    private var lastGeneration = -1
    private val cueInspector = CueInspector()
    private val shotInspector = ShotInspector()
    private val cutInspector = CutInspector()
    private val feedInspector = FeedInspector()
    private val actorInspector = ActorInspector()
    private val beatActorPanel = BeatActorPanel()
    private val actorTracksEditor = ActorTracksEditor()

    val currentKeyframeRow: Int get() = shotInspector.selectedKeyframeRow
    fun setCurrentKeyframeRow(row: Int) { shotInspector.setSelectedKeyframeRow(row) }

    fun render(scene: EditorScene, selection: SceneSelectionManager, playheadSeconds: Float) {
        if (!ImGui.begin(I18n.get("cobblemon.cinematic_editor.inspector.title") + EditorLayoutBuilder.WINDOW_INSPECTOR, ImGuiWindowFlags.HorizontalScrollbar)) { ImGui.end(); return }
        EditorDiagnostics.panel("Inspector")
        ImGui.pushTextWrapPos((ImGui.getWindowWidth() - ImGui.getStyle().windowPaddingX - ImGui.getStyle().scrollbarSize).coerceAtLeast(1f))

        val selected = selection.selection
        if (selected != lastSelection || lastScene !== scene || lastGeneration != scene.editGeneration) {
            syncBuffers(scene, selection.selection)
            lastSelection = selected
            lastScene = scene
            lastGeneration = scene.editGeneration
        }

        when (val sel = selection.selection) {
            SceneSelection.SceneRoot -> renderSceneProps(scene)
            is SceneSelection.ActorSelected -> renderActorProps(scene, sel.actorId)
            is SceneSelection.ShotSelected -> {
                val pending = selection.consumePendingKeyframeRowFocus()
                if (pending >= 0) setCurrentKeyframeRow(pending)
                renderShotProps(scene, sel.shotId, playheadSeconds)
            }
            is SceneSelection.FeedSelected -> renderFeedProps(scene, sel.index)
            is SceneSelection.BeatSelected -> renderBeatProps(scene, sel.beatId, selection)
            is SceneSelection.TransitionSelected -> renderTransitionProps(scene, sel.index)
            is SceneSelection.ParameterSelected -> renderParameterProps(scene, sel.name)
            is SceneSelection.CutSelected -> cutInspector.render(scene, sel.beatId, sel.feedId, sel.entryIndex)
            is SceneSelection.BeatActorSelected -> actorTracksEditor.render(scene, sel.beatId, sel.actorId)
        }

        ImGui.popTextWrapPos()
        ImGui.end()
    }

    private fun syncBuffers(scene: EditorScene, sel: SceneSelection) {
        when (sel) {
            SceneSelection.SceneRoot -> {
                sceneIdBuf.set(scene.id)
                startBeatBuf.set(scene.startBeatId)
            }
            is SceneSelection.BeatSelected -> {
                scene.beats[sel.beatId]?.let { beat ->
                    beatSpeedBuf.set(beat.speed)
                    beatDurationBuf.set(beat.duration ?: 0f)
                }
            }
            is SceneSelection.TransitionSelected -> {
                scene.transitions.getOrNull(sel.index)?.let {
                    transitionConditionBuf.set(it.condition)
                }
            }
            else -> {   }
        }
    }

    private fun renderSceneProps(scene: EditorScene) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.scene_metadata"))
        ImGui.separator()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.id"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##sceneId", sceneIdBuf)) {
            val newId = sceneIdBuf.get()
            if (newId.isNotBlank() && newId != scene.id) {
                scene.execute(RenameScene(oldId = scene.id, newId = newId))
            }
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.start_beat"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##startBeat", startBeatBuf)) {
            val newStart = startBeatBuf.get()
            if (newStart != scene.startBeatId && newStart in scene.beats) {
                scene.execute(ChangeStartBeat(oldStartBeatId = scene.startBeatId, newStartBeatId = newStart))
            }
        }

        ImGui.separator()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.takeover"))

        val immunityBool = ImBoolean(scene.damageImmunityDuringTakeover)
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.inspector.damage_immunity"), immunityBool)) {
            val newValue = immunityBool.get()
            if (newValue != scene.damageImmunityDuringTakeover) {
                scene.execute(
                    ChangeDamageImmunity(
                        oldValue = scene.damageImmunityDuringTakeover,
                        newValue = newValue
                    )
                )
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.beginTooltip()
            ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.damage_immunity_help"))
            ImGui.endTooltip()
        }

        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.disconnect_policy"))
        EditorWidgets.fullWidth()
        val currentPolicy = scene.disconnectPolicy
        if (ImGui.beginCombo("##disconnectPolicy", disconnectLabel(currentPolicy))) {
            for (option in DisconnectPolicy.entries) {
                if (ImGui.selectable(disconnectLabel(option), option == currentPolicy)) {
                    if (option != currentPolicy) {
                        scene.execute(
                            ChangeDisconnectPolicy(
                                oldValue = currentPolicy,
                                newValue = option
                            )
                        )
                    }
                }
                if (ImGui.isItemHovered()) {
                    ImGui.beginTooltip()
                    ImGui.text(
                        when (option) {
                            DisconnectPolicy.END_SCENE ->
                                I18n.get("cobblemon.cinematic_editor.inspector.disconnect.end_scene_help")
                            DisconnectPolicy.SUBSTITUTE ->
                                I18n.get("cobblemon.cinematic_editor.inspector.disconnect.substitute_help")
                            DisconnectPolicy.HIDE_ACTOR ->
                                I18n.get("cobblemon.cinematic_editor.inspector.disconnect.hide_actor_help")
                            DisconnectPolicy.FAIL ->
                                I18n.get("cobblemon.cinematic_editor.inspector.disconnect.fail_help")
                        }
                    )
                    ImGui.endTooltip()
                }
            }
            ImGui.endCombo()
        }
    }

    private fun renderActorProps(scene: EditorScene, actorId: String) {
        actorInspector.render(scene, actorId)
    }

    private fun renderShotProps(scene: EditorScene, shotId: String, playheadSeconds: Float) {
        shotInspector.render(scene, shotId, playheadSeconds)
    }

    private fun renderFeedProps(scene: EditorScene, index: Int) {
        feedInspector.render(scene, index)
    }

    private fun renderBeatProps(scene: EditorScene, beatId: String, selection: SceneSelectionManager) {
        val beat = scene.beats[beatId] ?: run {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.missing_beat", beatId))
            return
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.beat", beatId))
        ImGui.separator()

        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.speed"))
        EditorWidgets.fullWidth()
        if (ImGui.inputFloat("##beatSpeed", beatSpeedBuf)) {
            val newSpeed = beatSpeedBuf.get()
            if (newSpeed.isFinite() && newSpeed > 0f && newSpeed != beat.speed) {
                scene.execute(
                    ChangeBeatProperty<Float>(
                        beatId = beatId,
                        setter = { b, v -> b.speed = v },
                        oldValue = beat.speed,
                        newValue = newSpeed
                    )
                )
            }
        }

        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.inspector.duration"))
        EditorWidgets.fullWidth()
        if (ImGui.inputFloat("##beatDuration", beatDurationBuf)) {
            val duration = beatDurationBuf.get()
            val newDuration = duration.takeIf { it > 0f }
            if (duration.isFinite() && duration >= 0f && newDuration != beat.duration) {
                scene.execute(
                    ChangeBeatProperty<Float?>(
                        beatId = beatId,
                        setter = { b, v -> b.duration = v },
                        oldValue = beat.duration,
                        newValue = newDuration
                    )
                )
            }
        }

        val isLooping = beat.isLooping
        val loopingBool = ImBoolean(isLooping)
        if (ImGui.checkbox(I18n.get("cobblemon.cinematic_editor.inspector.looping"), loopingBool)) {
            scene.execute(
                ChangeBeatProperty<Boolean>(
                    beatId = beatId,
                    setter = { b, v -> b.isLooping = v },
                    oldValue = isLooping,
                    newValue = !isLooping
                )
            )
        }

        ImGui.separator()
        if (ImGui.beginTabBar("##beatTabs$beatId")) {
            if (ImGui.beginTabItem(I18n.get("cobblemon.cinematic_editor.inspector.cues", beat.cues.size) + "###cues")) {
                cueInspector.render(scene, beat)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(I18n.get("cobblemon.cinematic_editor.inspector.actors", beat.actorDirections.size) + "###actors")) {
                beatActorPanel.render(scene, beatId, selection)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }
    }

    private fun renderTransitionProps(scene: EditorScene, index: Int) {
        val tr = scene.transitions.getOrNull(index) ?: run {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.missing_transition", index))
            return
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.transition", tr.sourceBeatId, tr.targetBeatId))
        ImGui.separator()
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.condition"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##trCond", transitionConditionBuf)) {
            val newCondition = transitionConditionBuf.get()
            if (newCondition != tr.condition) {
                scene.execute(EditTransitionCondition(index, oldCondition = tr.condition, newCondition = newCondition))
            }
        }
    }

    private fun renderParameterProps(scene: EditorScene, name: String) {
        val param = scene.parameters[name] ?: return
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.parameter", name))
        ImGui.text(I18n.get("cobblemon.cinematic_editor.inspector.parameter_type", parameterTypeLabel(param.type)))
    }

    private fun disconnectLabel(policy: DisconnectPolicy): String = when (policy) {
        DisconnectPolicy.END_SCENE -> I18n.get("cobblemon.cinematic_editor.inspector.disconnect.end_scene")
        DisconnectPolicy.SUBSTITUTE -> I18n.get("cobblemon.cinematic_editor.inspector.disconnect.substitute")
        DisconnectPolicy.HIDE_ACTOR -> I18n.get("cobblemon.cinematic_editor.inspector.disconnect.hide_actor")
        DisconnectPolicy.FAIL -> I18n.get("cobblemon.cinematic_editor.inspector.disconnect.fail")
    }

    private fun parameterTypeLabel(type: ParameterType): String = when (type) {
        ParameterType.NUMBER -> I18n.get("cobblemon.cinematic_editor.inspector.parameter.number")
        ParameterType.VEC3 -> I18n.get("cobblemon.cinematic_editor.inspector.parameter.vec3")
        ParameterType.STRING -> I18n.get("cobblemon.cinematic_editor.inspector.parameter.string")
    }

}
