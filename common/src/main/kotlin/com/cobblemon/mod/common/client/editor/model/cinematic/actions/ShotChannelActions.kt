/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic.actions

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorScene
import com.cobblemon.mod.common.client.editor.model.cinematic.EditorShot
import com.cobblemon.mod.common.client.editor.model.cinematic.SceneEditorAction
import com.google.gson.JsonObject

enum class ShotChannel {
    POSITION, ROTATION, FOV, ROLL;

    fun set(shot: EditorShot, value: JsonObject?) {
        when (this) {
            POSITION -> shot.positionData = value ?: error("Position channel is required")
            ROTATION -> shot.rotationData = value ?: error("Rotation channel is required")
            FOV -> shot.fovData = value
            ROLL -> shot.rollData = value
        }
    }

    fun get(shot: EditorShot): JsonObject? = when (this) {
        POSITION -> shot.positionData
        ROTATION -> shot.rotationData
        FOV -> shot.fovData
        ROLL -> shot.rollData
    }
}

// Live JSON trees must never share children with undo snapshots.
class ChangeShotChannel(
    private val shotId: String,
    private val channel: ShotChannel,
    oldValue: JsonObject?,
    newValue: JsonObject?
) : SceneEditorAction {
    private val oldSnapshot = oldValue?.deepCopy()
    private val newSnapshot = newValue?.deepCopy()
    override fun execute(scene: EditorScene) {
        val shot = scene.shots[shotId] ?: return
        channel.set(shot, newSnapshot?.deepCopy())
    }
    override fun undo(scene: EditorScene) {
        val shot = scene.shots[shotId] ?: return
        channel.set(shot, oldSnapshot?.deepCopy())
    }
}

class ChangeShotModifier private constructor(
    private val shotId: String,
    private val index: Int,
    oldValue: JsonObject?,
    newValue: JsonObject?
) : SceneEditorAction {
    private val oldSnapshot = oldValue?.deepCopy()
    private val newSnapshot = newValue?.deepCopy()

    override fun execute(scene: EditorScene) = apply(scene, oldSnapshot, newSnapshot)
    override fun undo(scene: EditorScene) = apply(scene, newSnapshot, oldSnapshot)

    private fun apply(scene: EditorScene, previous: JsonObject?, value: JsonObject?) {
        val modifiers = scene.shots[shotId]?.modifiers ?: return
        when {
            previous == null && value != null -> modifiers.add(index.coerceIn(0, modifiers.size), value.deepCopy())
            value == null -> {
                val target = if (index == Int.MAX_VALUE) modifiers.lastIndex else index
                if (target in modifiers.indices) modifiers.removeAt(target)
            }
            index in modifiers.indices -> modifiers[index] = value.deepCopy()
        }
    }

    companion object {
        fun add(shotId: String, value: JsonObject): ChangeShotModifier {
            return ChangeShotModifier(shotId, Int.MAX_VALUE, oldValue = null, newValue = value)
        }
        fun remove(shotId: String, index: Int, existing: JsonObject): ChangeShotModifier =
            ChangeShotModifier(shotId, index, oldValue = existing, newValue = null)
        fun edit(shotId: String, index: Int, existing: JsonObject, replacement: JsonObject): ChangeShotModifier =
            ChangeShotModifier(shotId, index, oldValue = existing, newValue = replacement)
    }
}
