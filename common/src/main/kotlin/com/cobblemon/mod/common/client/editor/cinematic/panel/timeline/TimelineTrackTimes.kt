/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.model.EditorTrack
import com.google.gson.JsonObject

object TimelineTrackTimes {
    private fun arrayKey(trackType: String): String? = when (trackType) {
        "keyframe_movement" -> "keyframes"
        "animation_track" -> "cues"
        else -> null
    }

    fun times(track: EditorTrack): List<Float> {
        val key = arrayKey(track.trackType) ?: return emptyList()
        val arr = track.data.getAsJsonArray(key) ?: return emptyList()
        return (0 until arr.size()).map { arr[it].asJsonObject.get("time").asFloat }
    }

    fun withRetimedItem(track: EditorTrack, index: Int, newTime: Float): JsonObject {
        val key = arrayKey(track.trackType) ?: return track.data.deepCopy()
        val copy = track.data.deepCopy()
        val arr = copy.getAsJsonArray(key) ?: return copy
        if (index in 0 until arr.size()) arr[index].asJsonObject.addProperty("time", newTime)
        return copy
    }

    fun withItemRemoved(track: EditorTrack, index: Int): JsonObject {
        val key = arrayKey(track.trackType) ?: return track.data.deepCopy()
        val copy = track.data.deepCopy()
        val arr = copy.getAsJsonArray(key) ?: return copy
        if (index in 0 until arr.size()) arr.remove(index)
        return copy
    }
}
