/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.api.cinematic.model.ActorKind
import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.model.cinematic.ChangeSceneProperty
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorActor
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeActorKind
import imgui.ImGui
import net.minecraft.client.resources.language.I18n
import imgui.type.ImFloat
import imgui.type.ImString
import net.minecraft.world.phys.Vec3

class ActorInspector {

    private val speciesBuf = ImString(64).apply { inputData.isResizable = true }
    private val aspectBuf = ImString(64).apply { inputData.isResizable = true }
    private val categoryBuf = ImString(128).apply { inputData.isResizable = true }
    private val scaleBuf = ImFloat(1f)
    private val offsetBuf = FloatArray(3)
    private var syncedScene: EditorScene? = null
    private var syncedActor: EditorActor? = null
    private var syncedGeneration = -1

    private data class PreviewSettings(val species: String, val category: String, val scale: Float, val aspects: Set<String>, val offset: Vec3) {
        fun apply(actor: EditorActor) {
            actor.previewSpecies = species
            actor.previewCategory = category
            actor.previewScale = scale
            actor.previewOffset = offset
            actor.previewAspects.clear()
            actor.previewAspects.addAll(aspects)
        }
    }

    private fun settings(actor: EditorActor) = PreviewSettings(actor.previewSpecies, actor.previewCategory, actor.previewScale, actor.previewAspects.toSet(), actor.previewOffset)

    fun render(scene: EditorScene, actorId: String) {
        val actor = scene.actors[actorId] ?: run { ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.missing", actorId)); return }
        if (syncedScene !== scene || syncedActor !== actor || syncedGeneration != scene.editGeneration) {
            if (syncedScene !== scene || syncedActor !== actor) aspectBuf.set("")
            speciesBuf.set(actor.previewSpecies)
            categoryBuf.set(actor.previewCategory)
            scaleBuf.set(actor.previewScale)
            offsetBuf[0] = actor.previewOffset.x.toFloat()
            offsetBuf[1] = actor.previewOffset.y.toFloat()
            offsetBuf[2] = actor.previewOffset.z.toFloat()
            syncedScene = scene
            syncedActor = actor
            syncedGeneration = scene.editGeneration
        }
        val before = settings(actor)

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.title", actorId))
        ImGui.separator()

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.kind"))
        val kinds = listOf(
            ActorKind.PlayerTakeover to "cobblemon.cinematic_editor.actor.kind.player",
            ActorKind.RealEntity to "cobblemon.cinematic_editor.actor.kind.entity",
            ActorKind.SynthesizedPokemon to "cobblemon.cinematic_editor.actor.kind.pokemon",
            ActorKind.SynthesizedBedrock to "cobblemon.cinematic_editor.actor.kind.bedrock"
        )
        for ((kind, labelKey) in kinds) {
            val label = I18n.get(labelKey)
            if (ImGui.radioButton("$label###actorKind$kind", actor.kind == kind)) {
                if (actor.kind != kind) {
                    scene.execute(ChangeActorKind(actorId, actor.kind, kind))
                }
            }
        }

        ImGui.separator()
        val draft = EditorActor(actor.id, actor.kind).also { before.apply(it) }
        when (actor.kind) {
            ActorKind.PlayerTakeover -> ImGui.textDisabled(I18n.get("cobblemon.cinematic_editor.actor.player_binding"))
            ActorKind.RealEntity -> {
                renderPokemon(draft)
                ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor.standin_help"))
                ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.preview_offset"))
                EditorWidgets.fullWidth()
                val previous = offsetBuf.copyOf()
                if (ImGui.inputFloat3("##standInOffset", offsetBuf)) {
                    if (offsetBuf.all { it.isFinite() }) {
                        draft.previewOffset = Vec3(
                            if (offsetBuf[0] != previous[0]) offsetBuf[0].toDouble() else before.offset.x,
                            if (offsetBuf[1] != previous[1]) offsetBuf[1].toDouble() else before.offset.y,
                            if (offsetBuf[2] != previous[2]) offsetBuf[2].toDouble() else before.offset.z
                        )
                    } else previous.copyInto(offsetBuf)
                }
            }
            ActorKind.SynthesizedPokemon -> renderPokemon(draft)
            ActorKind.SynthesizedBedrock -> renderBedrock(draft)
        }
        val after = settings(draft)
        if (after != before) scene.execute(ChangeSceneProperty(
            actor, { target, value: PreviewSettings -> value.apply(target) }, before, after))
    }

    private fun renderPokemon(actor: EditorActor) {
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor.preview_help"))
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.species"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##actorSpecies${actor.id}", speciesBuf)) actor.previewSpecies = speciesBuf.get()

        renderAspectChips(actor)
    }

    private fun renderBedrock(actor: EditorActor) {
        ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.actor.preview_help"))
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.category"))
        EditorWidgets.fullWidth()
        if (ImGui.inputText("##actorCategory${actor.id}", categoryBuf)) actor.previewCategory = categoryBuf.get()

        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.scale"))
        EditorWidgets.fullWidth()
        if (inputFiniteFloat("##actorScale${actor.id}", scaleBuf)) {
            val v = scaleBuf.get().coerceAtLeast(0.01f); scaleBuf.set(v)
            actor.previewScale = v
        }

        renderAspectChips(actor)
    }

    private fun renderAspectChips(actor: EditorActor) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.actor.aspects", actor.previewAspects.size))
        actor.previewAspects.toList().forEach { aspect ->
            ImGui.bullet(); ImGui.sameLine(); ImGui.text(aspect)
            ImGui.sameLine()
            if (ImGui.smallButton("\u2715##rmAspect${actor.id}_$aspect")) actor.previewAspects.remove(aspect)
        }
        EditorWidgets.fullWidth()
        ImGui.inputText("##newAspect${actor.id}", aspectBuf)
        if (ImGui.smallButton(I18n.get("cobblemon.cinematic_editor.actor.add_aspect") + "##addAspect${actor.id}")) {
            val v = aspectBuf.get().trim()
            if (v.isNotEmpty()) { actor.previewAspects += v; aspectBuf.set("") }
        }
    }
}
