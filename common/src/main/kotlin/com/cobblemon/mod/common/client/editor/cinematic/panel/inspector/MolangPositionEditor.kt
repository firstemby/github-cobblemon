package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.cinematic.EditorWidgets
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.cobblemon.mod.common.client.editor.widget.ExpressionLikeField
import com.google.gson.JsonObject
import imgui.ImGui
import imgui.type.ImFloat
import imgui.type.ImString
import net.minecraft.client.resources.language.I18n

class MolangPositionEditor {
    private val axes = listOf("x", "y", "z")
    private val prefix = ImString(256).apply { inputData.isResizable = true }
    private val fallback = List(3) { ImFloat() }
    private val offset = List(3) { ImFloat() }
    private var expressions = emptyList<ExpressionLikeField>()
    private var synced: JsonObject? = null

    fun render(scene: EditorScene, shotId: String, data: JsonObject) {
        val variable = data.get("type").inspectorString() == "variable_vec3"
        if (synced !== data) {
            prefix.set(data.get("prefix").inspectorString())
            expressions = axes.map { axis -> ExpressionLikeField("$axis##position$shotId", data.get(axis).inspectorString("0.0")) }
            for ((key, buffers) in listOf("fallback" to fallback, "offset" to offset)) {
                axes.forEachIndexed { index, axis -> buffers[index].set(data.get(key).inspectorObject()?.get(axis).inspectorFloat()) }
            }
            synced = data
        }

        if (variable) {
            ImGui.textWrapped(I18n.get("cobblemon.cinematic_editor.molang_position.prefix"))
            EditorWidgets.fullWidth()
            if (ImGui.inputText("##positionPrefix$shotId", prefix)) {
                commit(scene, shotId, data) { it.addProperty("prefix", prefix.get()) }
            }
            vector(scene, shotId, data, "offset", offset)
        } else {
            ImGui.text(I18n.get("cobblemon.cinematic_editor.molang_position.expressions"))
            axes.forEachIndexed { index, axis ->
                EditorWidgets.fullWidth()
                val before = expressions[index].getValue()
                val value = expressions[index].render()
                if (value != before) commit(scene, shotId, data) { it.addProperty(axis, value) }
            }
        }
        vector(scene, shotId, data, "fallback", fallback)
    }

    private fun vector(scene: EditorScene, shotId: String, data: JsonObject, key: String, buffers: List<ImFloat>) {
        ImGui.text(I18n.get("cobblemon.cinematic_editor.molang_position.$key"))
        axes.forEachIndexed { index, axis ->
            EditorWidgets.fullWidth()
            if (inputFiniteFloat("$axis##position${key}$shotId", buffers[index])) {
                editInspectorPositionVector(scene, shotId, data, key, mapOf(axis to buffers[index].get().toDouble()))
                synced = scene.shots[shotId]?.positionData
            }
        }
    }

    private fun commit(scene: EditorScene, shotId: String, data: JsonObject, mutate: (JsonObject) -> Unit) {
        editInspectorChannel(scene, shotId, ShotChannel.POSITION, data, mutate)
        synced = scene.shots[shotId]?.positionData
    }
}
