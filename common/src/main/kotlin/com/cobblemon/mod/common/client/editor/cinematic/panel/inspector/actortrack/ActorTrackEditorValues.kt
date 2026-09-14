package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.inspectorObject
import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeActorTrackData
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal fun JsonElement?.trackArray(): JsonArray? =
    this?.takeIf { it.isJsonArray }?.asJsonArray

internal fun JsonObject.trackObject(key: String): JsonObject =
    get(key).inspectorObject() ?: JsonObject().also { add(key, it) }

internal fun JsonArray.trackRow(index: Int): JsonObject =
    get(index).inspectorObject() ?: JsonObject().also { set(index, it) }

internal fun editActorTrack(
    scene: EditorScene, beatId: String, actorId: String, index: Int, track: EditorTrack,
    mutate: (JsonObject) -> Unit
): Boolean {
    val current = scene.beats[beatId]?.actorDirections?.get(actorId)?.tracks?.getOrNull(index)
    if (current !== track) return false
    val updated = track.data.deepCopy()
    mutate(updated)
    if (updated == track.data) return false
    scene.execute(ChangeActorTrackData(beatId, actorId, index, track.data, updated))
    return true
}

internal fun editActorTrackVector(
    scene: EditorScene, beatId: String, actorId: String, index: Int, track: EditorTrack,
    key: String, values: Map<String, Double>
): Boolean {
    if (values.values.any { !it.toFloat().isFinite() }) return false
    return editActorTrack(scene, beatId, actorId, index, track) { updated ->
        val vector = updated.trackObject(key)
        values.forEach { (axis, value) -> vector.addProperty(axis, value) }
    }
}
