/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotModifier
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import imgui.ImGui
import net.minecraft.client.resources.language.I18n

class ShotInspector {

    private val fixedPos = FixedPositionEditor()
    private val fixedRot = FixedRotationEditor()
    private val fixedFov = FixedFovEditor()
    private val fixedRoll = FixedRollEditor()
    private val keyframePos = KeyframePositionEditor()
    private val lookAt = LookAtTargetEditor()
    private val smoothing = SmoothingModifierEditor()
    private val lensSection = LensSection()

    private val keyframeRot = KeyframeRotationEditor()
    private val lookAlongPath = LookAlongPathEditor()
    private val keyframeFov = KeyframeFovEditor()
    private val expressionFov = ExpressionFovEditor()
    private val keyframeRoll = KeyframeRollEditor()
    private val shakeDerivedRoll = ShakeDerivedRollEditor()
    private val splinePos = SplinePositionEditor()
    private val orbitPos = OrbitPositionEditor()
    private val dollyPos = DollyPositionEditor()
    private val truckPos = TruckPositionEditor()
    private val cranePos = CranePositionEditor()
    private val followActorPos = FollowActorPositionEditor()
    private val molangPos = MolangPositionEditor()
    private val shake = ShakeModifierEditor()
    private val offset = OffsetModifierEditor()
    private val constraint = ConstraintModifierEditor()

    val selectedKeyframeRow: Int get() = keyframePos.currentSelectionRow
    fun setSelectedKeyframeRow(row: Int) { keyframePos.setSelectedRow(row) }

    fun render(scene: EditorScene, shotId: String, playheadSeconds: Float) {
        val shot = scene.shots[shotId] ?: run {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.shot_missing", shotId))
            return
        }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.shot_title", shotId))
        ImGui.separator()

        section("position") {
            ShotSourceTypeDropdown.render("type##posType$shotId", SourceChannel.POSITION, shot.positionData.get("type").inspectorString()) { entry ->
                val old = shot.positionData
                scene.execute(ChangeShotChannel(shotId, ShotChannel.POSITION, old, entry.defaultJson()))
            }
            when (shot.positionData.get("type").inspectorString()) {
                "fixed" -> fixedPos.render(scene, shotId, shot.positionData)
                "keyframe" -> keyframePos.render(scene, shotId, shot.positionData, playheadSeconds)
                "spline" -> splinePos.render(scene, shotId, shot.positionData)
                "orbit" -> orbitPos.render(scene, shotId, shot.positionData)
                "dolly" -> dollyPos.render(scene, shotId, shot.positionData)
                "truck" -> truckPos.render(scene, shotId, shot.positionData)
                "crane" -> cranePos.render(scene, shotId, shot.positionData)
                "follow_actor" -> followActorPos.render(scene, shotId, shot.positionData)
                "variable_vec3", "expression_vec3" -> molangPos.render(scene, shotId, shot.positionData)
                else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unsupported_source"))
            }
        }

        section("rotation") {
            ShotSourceTypeDropdown.render("type##rotType$shotId", SourceChannel.ROTATION, shot.rotationData.get("type").inspectorString()) { entry ->
                val old = shot.rotationData
                scene.execute(ChangeShotChannel(shotId, ShotChannel.ROTATION, old, entry.defaultJson()))
            }
            when (shot.rotationData.get("type").inspectorString()) {
                "fixed" -> fixedRot.render(scene, shotId, shot.rotationData)
                "look_at" -> lookAt.render(scene, shotId, shot.rotationData)
                "keyframe" -> keyframeRot.render(scene, shotId, shot.rotationData)
                "look_along_path" -> lookAlongPath.render(scene, shotId, shot.rotationData)
                else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unsupported_source"))
            }
        }

        section("fov") {
            renderOptionalChannel(
                scene, shotId,
                current = shot.fovData,
                channel = ShotChannel.FOV,
                sourceChannel = SourceChannel.FOV,
                onRender = { data ->
                    when (data.get("type").inspectorString()) {
                        "fixed" -> fixedFov.render(scene, shotId, data)
                        "keyframe" -> keyframeFov.render(scene, shotId, data)
                        "expression" -> expressionFov.render(scene, shotId, data)
                        else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unsupported_source"))
                    }
                }
            )
        }

        section("roll") {
            renderOptionalChannel(
                scene, shotId,
                current = shot.rollData,
                channel = ShotChannel.ROLL,
                sourceChannel = SourceChannel.ROLL,
                onRender = { data ->
                    when (data.get("type").inspectorString()) {
                        "fixed" -> fixedRoll.render(scene, shotId, data)
                        "keyframe" -> keyframeRoll.render(scene, shotId, data)
                        "shake" -> shakeDerivedRoll.render(scene, shotId, data)
                        else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unsupported_source"))
                    }
                }
            )
        }

        section("lens") {
            lensSection.render(scene, shotId)
        }

        section("modifiers", shot.modifiers.size) {
            var modifierToRemove = -1
            shot.modifiers.forEachIndexed { i, mod ->
                ImGui.pushID("mod$shotId$i")
                try {
                    val type = mod.get("type").inspectorString()
                    ImGui.text("[$i] ${ShotSourceTypes.entryFor(SourceChannel.MODIFIER, type)?.label ?: type}")
                    ImGui.sameLine()
                    if (ImGui.smallButton("\u2715")) {
                        modifierToRemove = i
                    }
                    when (mod.get("type").inspectorString()) {
                        "smoothing" -> smoothing.render(scene, shotId, i, mod)
                        "shake" -> shake.render(scene, shotId, i, mod)
                        "offset" -> offset.render(scene, shotId, i, mod)
                        "constraint" -> constraint.render(scene, shotId, i, mod)
                        else -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.unsupported_source"))
                    }
                } finally {
                    ImGui.popID()
                }
            }
            if (modifierToRemove >= 0) {
                scene.execute(ChangeShotModifier.remove(shotId, modifierToRemove, shot.modifiers[modifierToRemove]))
            }
            ShotSourceTypeDropdown.render("+ add modifier##mod$shotId", SourceChannel.MODIFIER, null) { entry ->
                scene.execute(ChangeShotModifier.add(shotId, entry.defaultJson()))
            }
        }
    }

    private inline fun section(key: String, vararg args: Any, body: () -> Unit) {
        if (ImGui.collapsingHeader(I18n.get("cobblemon.cinematic_editor.$key", *args) + "###$key")) {
            ImGui.indent()
            try { body() } finally { ImGui.unindent() }
        }
    }

    private inline fun renderOptionalChannel(
        scene: EditorScene,
        shotId: String,
        current: JsonObject?,
        channel: ShotChannel,
        sourceChannel: SourceChannel,
        onRender: (JsonObject) -> Unit
    ) {
        if (current == null) {
            val label = I18n.get("cobblemon.cinematic_editor.${channel.name.lowercase()}")
            if (ImGui.button(I18n.get("cobblemon.cinematic_editor.add_channel", label) + "##add$shotId")) {
                scene.execute(ChangeShotChannel(shotId, channel,
                    oldValue = null,
                    newValue = ShotSourceTypes.forChannel(sourceChannel).first().defaultJson()))
            }
            return
        }
        ShotSourceTypeDropdown.render("type##$channel$shotId", sourceChannel, current.get("type").inspectorString()) { entry ->
            scene.execute(ChangeShotChannel(shotId, channel, current, entry.defaultJson()))
        }
        val updated = scene.shots[shotId]?.let { channel.get(it) } ?: return
        ImGui.sameLine()
        if (ImGui.button(I18n.get("cobblemon.cinematic_editor.clear") + "##$channel$shotId")) {
            scene.execute(ChangeShotChannel(shotId, channel, updated, newValue = null))
            return
        }
        onRender(updated)
    }
}
