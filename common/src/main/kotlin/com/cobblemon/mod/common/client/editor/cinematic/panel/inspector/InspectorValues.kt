package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotModifier
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import imgui.ImGui
import imgui.type.ImFloat

internal fun JsonElement?.inspectorFloat(default: Float = 0f): Float {
    if (this == null || !isJsonPrimitive || !asJsonPrimitive.isNumber) return default
    return asFloat.takeIf { it.isFinite() } ?: default
}

internal fun JsonElement?.inspectorString(default: String = ""): String =
    if (this != null && isJsonPrimitive) asString else default

internal fun JsonElement?.inspectorObject(): JsonObject? =
    this?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonElement?.inspectorBoolean(default: Boolean = false): Boolean =
    if (this != null && isJsonPrimitive && asJsonPrimitive.isBoolean) asBoolean else default

internal fun editInspectorPositionVector(
    scene: EditorScene, shotId: String, source: JsonObject, key: String, values: Map<String, Double>
): Boolean {
    if (values.values.any { !it.toFloat().isFinite() }) return false
    return editInspectorChannel(scene, shotId, ShotChannel.POSITION, source) { updated ->
        val vector = updated.get(key).inspectorObject() ?: JsonObject().also { updated.add(key, it) }
        values.forEach { (axis, value) -> vector.addProperty(axis, value) }
    }
}

internal fun inputFiniteFloat(id: String, buffer: ImFloat): Boolean {
    val previous = buffer.get()
    if (!ImGui.inputFloat(id, buffer)) return false
    if (buffer.get().isFinite()) return true
    buffer.set(previous)
    return false
}

internal fun sliderFiniteFloat(id: String, buffer: FloatArray, min: Float, max: Float): Boolean {
    val previous = buffer[0]
    if (!ImGui.sliderFloat(id, buffer, min, max)) return false
    if (buffer[0].isFinite()) {
        buffer[0] = buffer[0].coerceIn(min, max)
        return true
    }
    buffer[0] = previous
    return false
}

internal fun editInspectorModifier(
    scene: EditorScene, shotId: String, index: Int, source: JsonObject, mutate: (JsonObject) -> Unit
): Boolean {
    val current = scene.shots[shotId]?.modifiers?.getOrNull(index) ?: return false
    if (current.get("type") != source.get("type")) return false
    val updated = current.deepCopy()
    mutate(updated)
    if (updated == current) return false
    scene.execute(ChangeShotModifier.edit(shotId, index, current, updated))
    return true
}

internal fun editInspectorChannel(
    scene: EditorScene, shotId: String, channel: ShotChannel, source: JsonObject,
    mutate: (JsonObject) -> Unit
): Boolean {
    val shot = scene.shots[shotId] ?: return false
    val current = channel.get(shot) ?: return false
    if (current.get("type") != source.get("type")) return false
    val updated = current.deepCopy()
    mutate(updated)
    if (updated == current) return false
    scene.execute(ChangeShotChannel(shotId, channel, current, updated))
    return true
}
