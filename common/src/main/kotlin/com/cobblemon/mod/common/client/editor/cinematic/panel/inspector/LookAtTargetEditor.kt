/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString
import net.minecraft.world.phys.Vec3

enum class LookAtKind(val typeId: String, private val labelKey: String) {
    POSITION("position", "cobblemon.cinematic_editor.look_at.kind.position"),
    ACTOR("actor", "cobblemon.cinematic_editor.look_at.kind.actor"),
    ACTORS("actors", "cobblemon.cinematic_editor.look_at.kind.actors"),
    ACTOR_BONE("actor_bone", "cobblemon.cinematic_editor.look_at.kind.actor_bone"),
    VARIABLE("variable", "cobblemon.cinematic_editor.look_at.kind.variable"),
    EXPRESSION("expression", "cobblemon.cinematic_editor.look_at.kind.expression");

    val label get() = I18n.get(labelKey)
}

class LookAtTargetEditor {

    private val actorIdBuf = ImString(64).apply { inputData.isResizable = true }
    private val boneBuf = ImString(64).apply { inputData.isResizable = true }
    private val varPrefixBuf = ImString(64).apply { inputData.isResizable = true }
    private val xExprBuf = ImString(128).apply { inputData.isResizable = true }
    private val yExprBuf = ImString(128).apply { inputData.isResizable = true }
    private val zExprBuf = ImString(128).apply { inputData.isResizable = true }
    private val posXBuf = ImFloat()
    private val posYBuf = ImFloat()
    private val posZBuf = ImFloat()
    private var syncedData: JsonObject? = null
    private var syncedScene: EditorScene? = null
    private var syncedShotId: String? = null
    private var syncedTarget: JsonObject? = null
    private data class PendingSwap(
        val scene: EditorScene, val shotId: String, val data: JsonObject,
        val target: JsonElement?, val kind: LookAtKind
    )
    private var pendingSwap: PendingSwap? = null
    private var pendingPopup: String? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val target = data.get("target").inspectorObject() ?: buildDefaultTarget(LookAtKind.POSITION)
        if (scene !== syncedScene || shotId != syncedShotId || data !== syncedData || target != syncedTarget) {
            pendingPopup?.let { popup ->
                if (ImGui.beginPopupModal(popup)) {
                    try { ImGui.closeCurrentPopup() } finally { ImGui.endPopup() }
                }
            }
            pendingSwap = null
            pendingPopup = null
            syncBuffers(target)
            syncedScene = scene
            syncedShotId = shotId
            syncedData = data
            syncedTarget = target.deepCopy()
        }

        val currentKind = LookAtKind.entries.firstOrNull { it.typeId == target.get("type").inspectorString() } ?: LookAtKind.POSITION
        var swapped = false
        if (ImGui.beginCombo(I18n.get("cobblemon.cinematic_editor.look_at.target_kind") + "##$shotId", currentKind.label)) {
            try {
                for (kind in LookAtKind.entries) {
                    val selected = kind == currentKind
                    if (ImGui.selectable(kind.label, selected) && !selected) {
                        if (hasUserData(target)) {
                            requestSwap(scene, shotId, data, kind)
                            pendingPopup = I18n.get("cobblemon.cinematic_editor.look_at.discard_title") + "##$shotId"
                        } else {
                            applySwap(scene, shotId, data, kind)
                            swapped = true
                        }
                    }
                    if (selected) ImGui.setItemDefaultFocus()
                }
            } finally {
                ImGui.endCombo()
            }
        }
        if (swapped) return

        pendingSwap?.let { request ->
            val next = request.kind
            val popup = checkNotNull(pendingPopup)
            ImGui.openPopup(popup)
            if (ImGui.beginPopupModal(popup)) {
                try {
                    ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.discard_detail"))
                    ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.discard_question", next.label))
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.swap"))) {
                        swapped = confirmSwap(scene, shotId, data)
                        ImGui.closeCurrentPopup()
                    }
                    ImGui.sameLine()
                    if (ImGui.button(I18n.get("cobblemon.cinematic_editor.keep"))) {
                        pendingSwap = null
                        pendingPopup = null
                        ImGui.closeCurrentPopup()
                    }
                } finally {
                    ImGui.endPopup()
                }
            }
        }
        if (swapped) return

        ImGui.separator()
        when (currentKind) {
            LookAtKind.POSITION -> renderPosition(scene, shotId, data, target)
            LookAtKind.ACTOR -> renderActor(scene, shotId, data, target)
            LookAtKind.ACTORS -> {
                ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.group_center"))
                val actorIds = target.get("actorIds")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.map { it.inspectorString() }?.filter { it.isNotBlank() }.orEmpty()
                ActorGroupPicker.render("aim$shotId", scene, actorIds) { selected ->
                    if (selected.isNotEmpty()) commitTarget(scene, shotId, data, target) { json ->
                        json.add("actorIds", JsonArray().apply { selected.forEach { add(it) } })
                    }
                }
            }
            LookAtKind.ACTOR_BONE -> renderActorBone(scene, shotId, data, target)
            LookAtKind.VARIABLE -> renderVariable(scene, shotId, data, target)
            LookAtKind.EXPRESSION -> renderExpression(scene, shotId, data, target)
        }
    }

    internal fun requestSwap(scene: EditorScene, shotId: String, data: JsonObject, next: LookAtKind) {
        pendingSwap = PendingSwap(scene, shotId, data, data.get("target")?.deepCopy(), next)
    }

    internal fun confirmSwap(scene: EditorScene, shotId: String, data: JsonObject): Boolean {
        val request = pendingSwap ?: return false
        pendingSwap = null
        pendingPopup = null
        if (scene !== request.scene || shotId != request.shotId || data !== request.data ||
            data.get("target") != request.target || scene.shots[shotId]?.rotationData !== data) return false
        return applySwap(scene, shotId, data, request.kind)
    }

    private fun applySwap(scene: EditorScene, shotId: String, data: JsonObject, next: LookAtKind): Boolean {
        val changed = editInspectorChannel(scene, shotId, ShotChannel.ROTATION, data) { it.add("target", buildDefaultTarget(next)) }
        syncedData = null
        return changed
    }

    private fun renderPosition(scene: EditorScene, shotId: String, channel: JsonObject, target: JsonObject) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.world_position"))
        ImGui.setNextItemWidth(80f); if (inputFiniteFloat("##lapX$shotId", posXBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("x", posXBuf.get().toDouble()) }
        ImGui.sameLine(); ImGui.setNextItemWidth(80f); if (inputFiniteFloat("##lapY$shotId", posYBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("y", posYBuf.get().toDouble()) }
        ImGui.sameLine(); ImGui.setNextItemWidth(80f); if (inputFiniteFloat("##lapZ$shotId", posZBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("z", posZBuf.get().toDouble()) }
        ImGui.sameLine()
        SetFromCameraButton.render("lap$shotId") capture@ { cam ->
            if (!cam.x.toFloat().isFinite() || !cam.y.toFloat().isFinite() || !cam.z.toFloat().isFinite()) return@capture
            posXBuf.set(cam.x.toFloat()); posYBuf.set(cam.y.toFloat()); posZBuf.set(cam.z.toFloat())
            commitTarget(scene, shotId, channel, target) {
                it.addProperty("x", cam.x); it.addProperty("y", cam.y); it.addProperty("z", cam.z)
            }
        }
    }

    private fun renderActor(scene: EditorScene, shotId: String, channel: JsonObject, target: JsonObject) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.actor_id"))
        ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##laActor$shotId", actorIdBuf)) {
            commitTarget(scene, shotId, channel, target) { it.addProperty("actorId", actorIdBuf.get()) }
        }
    }

    private fun renderActorBone(scene: EditorScene, shotId: String, channel: JsonObject, target: JsonObject) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.actor_id")); ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##laActorBoneActor$shotId", actorIdBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("actorId", actorIdBuf.get()) }
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.bone_name")); ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##laActorBoneBone$shotId", boneBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("bone", boneBuf.get()) }
    }

    private fun renderVariable(scene: EditorScene, shotId: String, channel: JsonObject, target: JsonObject) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.variable_prefix"))
        ImGui.setNextItemWidth(200f)
        if (ImGui.inputText("##laVar$shotId", varPrefixBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("prefix", varPrefixBuf.get()) }
    }

    private fun renderExpression(scene: EditorScene, shotId: String, channel: JsonObject, target: JsonObject) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.look_at.expressions"))
        ImGui.setNextItemWidth(200f); if (ImGui.inputText("x##laExprX$shotId", xExprBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("x", xExprBuf.get()) }
        ImGui.setNextItemWidth(200f); if (ImGui.inputText("y##laExprY$shotId", yExprBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("y", yExprBuf.get()) }
        ImGui.setNextItemWidth(200f); if (ImGui.inputText("z##laExprZ$shotId", zExprBuf)) commitTarget(scene, shotId, channel, target) { it.addProperty("z", zExprBuf.get()) }
    }

    private fun syncBuffers(target: JsonObject) {
        val position = positionForBuffers(target)
        posXBuf.set(position.x.toFloat())
        posYBuf.set(position.y.toFloat())
        posZBuf.set(position.z.toFloat())
        actorIdBuf.set(target.get("actorId").inspectorString())
        boneBuf.set(target.get("bone").inspectorString())
        varPrefixBuf.set(target.get("prefix").inspectorString())
        xExprBuf.set(target.get("x").inspectorString())
        yExprBuf.set(target.get("y").inspectorString())
        zExprBuf.set(target.get("z").inspectorString())
    }

    private fun commitTarget(
        scene: EditorScene,
        shotId: String,
        channel: JsonObject,
        target: JsonObject,
        mutate: (JsonObject) -> Unit
    ) {
        editInspectorChannel(scene, shotId, ShotChannel.ROTATION, channel) { updated ->
            val copy = updated.get("target").inspectorObject() ?: target.deepCopy()
            if (copy.get("type") != target.get("type")) return@editInspectorChannel
            mutate(copy)
            updated.add("target", copy)
        }
        syncedData = null
    }

    companion object {
        internal fun positionForBuffers(target: JsonObject): Vec3 {
            if (target.get("type").inspectorString("position") != "position") return Vec3.ZERO
            return Vec3(target.get("x").inspectorFloat().toDouble(), target.get("y").inspectorFloat().toDouble(),
                target.get("z").inspectorFloat().toDouble())
        }

        fun buildDefaultTarget(kind: LookAtKind): JsonObject = when (kind) {
            LookAtKind.POSITION -> JsonObject().apply {
                addProperty("type", "position"); addProperty("x", 0.0); addProperty("y", 64.0); addProperty("z", 0.0)
            }
            LookAtKind.ACTOR -> JsonObject().apply {
                addProperty("type", "actor"); addProperty("actorId", "target")
            }
            LookAtKind.ACTORS -> JsonObject().apply {
                addProperty("type", "actors"); add("actorIds", JsonArray().apply { add("user"); add("target") })
            }
            LookAtKind.ACTOR_BONE -> JsonObject().apply {
                addProperty("type", "actor_bone"); addProperty("actorId", "target"); addProperty("bone", "root")
            }
            LookAtKind.VARIABLE -> JsonObject().apply {
                addProperty("type", "variable"); addProperty("prefix", "")
            }
            LookAtKind.EXPRESSION -> JsonObject().apply {
                addProperty("type", "expression"); addProperty("x", "0.0"); addProperty("y", "0.0"); addProperty("z", "0.0")
            }
        }

        fun hasUserData(target: JsonObject): Boolean {
            val kind = LookAtKind.entries.firstOrNull { it.typeId == target.get("type").inspectorString() }
                ?: return target.size() > 0
            val defaults = buildDefaultTarget(kind)
            return target.entrySet().any { (key, value) -> value != defaults.get(key) }
        }
    }
}
