/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.model.cinematic

import com.cobblemon.mod.common.api.camera.model.HudElement
import com.cobblemon.mod.common.api.cinematic.model.SceneCue
import com.google.gson.JsonObject
import org.joml.Vector2f

class EditorBeat(
    var id: String,
    var duration: Float? = null,
    var isLooping: Boolean = false,
    var speed: Float = 1.0f,
    val actorDirections: MutableMap<String, EditorActorDirection> = mutableMapOf(),
    val cuts: MutableMap<String, EditorCutTrack> = mutableMapOf(),
    var renderPlayerBody: Boolean = true,
    val hiddenHudElements: MutableSet<HudElement> = mutableSetOf(),
    var nodePosition: Vector2f = Vector2f(0f, 0f),
    var guiTrackData: JsonObject? = null,
    var cues: List<SceneCue> = emptyList()
)
