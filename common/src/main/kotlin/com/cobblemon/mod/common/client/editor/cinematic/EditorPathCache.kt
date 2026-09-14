/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic

import com.cobblemon.mod.common.api.cinematic.model.sources.builtin.SplinePosition
import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.SplineMovement
import com.google.gson.JsonObject

object EditorPathCache {
    private data class Entry(val json: JsonObject, val value: Any)
    private val entries = mutableMapOf<String, Entry>()
    fun actor(key: String, json: JsonObject): SplineMovement = cached("actor:$key", json) { SplineMovement.fromJson(json) }
    fun shot(key: String, json: JsonObject): SplinePosition = cached("shot:$key", json) { SplinePosition.fromJson(json) }
    fun clear() = entries.clear()

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: String, json: JsonObject, compile: () -> T): T {
        entries[key]?.takeIf { it.json == json }?.let { return it.value as T }
        val value = compile()
        if (entries.size >= 256) entries.clear()
        entries[key] = Entry(json.deepCopy(), value)
        return value
    }
}
