/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeActorTrackData
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ChangeShotChannel
import com.cobblemon.mod.common.client.editor.model.cinematic.actions.ShotChannel
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

sealed interface EditorGrabTarget {
    fun owningJson(scene: EditorScene): JsonObject?
    fun position(scene: EditorScene): Vec3?
    fun moveTo(scene: EditorScene, x: Double, y: Double, z: Double)
    fun commit(scene: EditorScene, before: JsonObject)
    fun isolate(scene: EditorScene)

    fun restore(scene: EditorScene, before: JsonObject) {
        val current = owningJson(scene) ?: return
        current.keySet().toList().forEach { current.remove(it) }
        before.entrySet().forEach { (key, value) -> current.add(key, value.deepCopy()) }
    }
}

private fun rowPosition(root: JsonObject?, row: Int): JsonObject? {
    val arr = runCatching { root?.getAsJsonArray("keyframes") }.getOrNull() ?: return null
    if (row !in 0 until arr.size()) return null
    return runCatching { arr[row].asJsonObject.getAsJsonObject("position") }.getOrNull()
}

private fun readVec(pos: JsonObject?): Vec3? {
    val values = runCatching {
        Triple(pos?.get("x")?.asDouble, pos?.get("y")?.asDouble, pos?.get("z")?.asDouble)
    }.getOrNull() ?: return null
    val (x, y, z) = values
    if (x == null || y == null || z == null || !x.isFinite() || !y.isFinite() || !z.isFinite()) return null
    return Vec3(x, y, z)
}

private fun writeVec(pos: JsonObject?, x: Double, y: Double, z: Double) {
    if (pos == null || !x.isFinite() || !y.isFinite() || !z.isFinite()) return
    pos.addProperty("x", x)
    pos.addProperty("y", y)
    pos.addProperty("z", z)
}

sealed class ShotPositionTarget(val shotId: String) : EditorGrabTarget {
    protected abstract fun vec(scene: EditorScene): JsonObject?

    override fun owningJson(scene: EditorScene): JsonObject? = scene.shots[shotId]?.positionData

    override fun position(scene: EditorScene): Vec3? = readVec(vec(scene))

    override fun moveTo(scene: EditorScene, x: Double, y: Double, z: Double) =
        writeVec(vec(scene), x, y, z)

    override fun isolate(scene: EditorScene) {
        val shot = scene.shots[shotId] ?: return
        shot.positionData = shot.positionData.deepCopy()
    }

    override fun commit(scene: EditorScene, before: JsonObject) {
        val shot = scene.shots[shotId] ?: return
        val after = shot.positionData.deepCopy()
        if (after == before) return
        scene.execute(ChangeShotChannel(shotId, ShotChannel.POSITION, before.deepCopy(), after))
        isolate(scene)
    }
}

class ShotKeyframeTarget(shotId: String, val row: Int) : ShotPositionTarget(shotId) {
    override fun vec(scene: EditorScene): JsonObject? = rowPosition(owningJson(scene), row)

    override fun equals(other: Any?): Boolean =
        other is ShotKeyframeTarget && other.shotId == shotId && other.row == row

    override fun hashCode(): Int = 31 * shotId.hashCode() + row
}

class SplinePointTarget(shotId: String, val index: Int) : ShotPositionTarget(shotId) {
    override fun vec(scene: EditorScene): JsonObject? = runCatching {
        val arr = owningJson(scene)?.getAsJsonArray("controlPoints") ?: return null
        if (index !in 0 until arr.size()) return null
        arr[index].asJsonObject
    }.getOrNull()

    override fun equals(other: Any?): Boolean =
        other is SplinePointTarget && other.shotId == shotId && other.index == index

    override fun hashCode(): Int = 31 * shotId.hashCode() + index
}

sealed class ActorPositionTarget(
    val beatId: String,
    val actorId: String,
    val trackIndex: Int
) : EditorGrabTarget {
    protected abstract fun vec(scene: EditorScene): JsonObject?

    private fun track(scene: EditorScene) =
        scene.beats[beatId]?.actorDirections?.get(actorId)?.tracks?.getOrNull(trackIndex)

    override fun owningJson(scene: EditorScene): JsonObject? = track(scene)?.data

    override fun position(scene: EditorScene): Vec3? = readVec(vec(scene))

    override fun moveTo(scene: EditorScene, x: Double, y: Double, z: Double) =
        writeVec(vec(scene), x, y, z)

    override fun commit(scene: EditorScene, before: JsonObject) {
        val track = track(scene) ?: return
        val after = track.data.deepCopy()
        if (after == before) return
        scene.execute(ChangeActorTrackData(beatId, actorId, trackIndex, before.deepCopy(), after))
        isolate(scene)
    }

    override fun isolate(scene: EditorScene) {
        val track = track(scene) ?: return
        val isolated = track.data.deepCopy()
        track.data.keySet().toList().forEach { track.data.remove(it) }
        isolated.entrySet().forEach { (key, value) -> track.data.add(key, value) }
    }
}

class ActorKeyframeTarget(beatId: String, actorId: String, trackIndex: Int, val row: Int) :
    ActorPositionTarget(beatId, actorId, trackIndex) {
    override fun vec(scene: EditorScene): JsonObject? = rowPosition(owningJson(scene), row)

    override fun equals(other: Any?): Boolean = other is ActorKeyframeTarget &&
        beatId == other.beatId && actorId == other.actorId && trackIndex == other.trackIndex && row == other.row

    override fun hashCode(): Int = listOf(beatId, actorId, trackIndex, row).hashCode()
}

class ActorSplinePointTarget(beatId: String, actorId: String, trackIndex: Int, val index: Int) :
    ActorPositionTarget(beatId, actorId, trackIndex) {
    override fun vec(scene: EditorScene): JsonObject? = runCatching {
        val points = owningJson(scene)?.getAsJsonArray("controlPoints") ?: return null
        if (index !in 0 until points.size()) return null
        points[index].asJsonObject
    }.getOrNull()

    override fun equals(other: Any?): Boolean = other is ActorSplinePointTarget &&
        beatId == other.beatId && actorId == other.actorId && trackIndex == other.trackIndex && index == other.index

    override fun hashCode(): Int = listOf(beatId, actorId, trackIndex, index).hashCode()
}
