package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject

internal fun JsonObject.keyframeRows(): JsonArray? =
    get("keyframes")?.takeIf { it.isJsonArray }?.asJsonArray

internal class KeyframeChannelDraft(
    private val scene: EditorScene,
    private val shotId: String,
    private val channel: ShotChannel,
    source: JsonObject
) {
    var data: JsonObject = source
        private set

    fun edit(mutate: (JsonObject) -> Unit): Boolean {
        val shot = scene.shots[shotId] ?: return false
        val current = channel.get(shot) ?: return false
        if (current !== data) return false
        val updated = current.deepCopy()
        mutate(updated)
        if (updated == current) return false
        scene.execute(ChangeShotChannel(shotId, channel, current, updated))
        data = requireNotNull(channel.get(shot))
        return true
    }

    fun editRow(index: Int, mutate: (JsonObject) -> Unit): Boolean = edit { updated ->
        val rows = updated.keyframeRows() ?: return@edit
        if (index !in 0 until rows.size()) return@edit
        val row = rows[index].inspectorObject() ?: JsonObject().also { rows.set(index, it) }
        mutate(row)
    }

    fun editValues(index: Int, values: Map<String, Float>): Boolean {
        if (values.values.any { !it.isFinite() }) return false
        return editRow(index) { row ->
            values.forEach { (key, value) ->
                row.addProperty(key, when (key) {
                    "time" -> value.coerceAtLeast(0f)
                    "fov" -> value.coerceIn(1f, 179f)
                    else -> value
                })
            }
        }
    }

    fun editPosition(index: Int, values: Map<String, Double>): Boolean {
        if (values.values.any { !it.toFloat().isFinite() }) return false
        return editRow(index) { row ->
            val position = row.get("position").inspectorObject() ?: JsonObject().also { row.add("position", it) }
            values.forEach { (axis, value) -> position.addProperty(axis, value) }
        }
    }

    fun append(row: JsonObject): Boolean = edit { updated ->
        val rows = updated.keyframeRows() ?: JsonArray().also { updated.add("keyframes", it) }
        rows.add(row.deepCopy())
    }

    fun remove(index: Int): Boolean = edit { updated ->
        val rows = updated.keyframeRows() ?: return@edit
        if (index in 0 until rows.size()) rows.remove(index)
    }
}
