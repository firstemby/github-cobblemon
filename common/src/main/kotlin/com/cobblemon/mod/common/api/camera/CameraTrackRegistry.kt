/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.JsonObject

object CameraTrackRegistry {
    private val trackDeserializers = mutableMapOf<String, (JsonObject) -> ICameraTrack>()

    fun registerTrackType(trackType: String, deserializer: (JsonObject) -> ICameraTrack) {
        if (trackDeserializers.containsKey(trackType)) {
            Cobblemon.LOGGER.warn("[CameraTrackRegistry] Track type '$trackType' is already registered, overwriting")
        }
        trackDeserializers[trackType] = deserializer
        Cobblemon.LOGGER.debug("[CameraTrackRegistry] Registered camera track type: $trackType")
    }

    fun deserializeTrack(trackType: String, json: JsonObject): ICameraTrack? {
        val deserializer = trackDeserializers[trackType]
        if (deserializer == null) {
            Cobblemon.LOGGER.warn("[CameraTrackRegistry] Unknown track type: $trackType")
            return null
        }
        return try {
            deserializer(json)
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("[CameraTrackRegistry] Failed to deserialize track type '$trackType': ${e.message}", e)
            null
        }
    }

    fun getRegisteredTrackTypes(): Set<String> = trackDeserializers.keys.toSet()

    fun isTrackTypeRegistered(trackType: String): Boolean = trackDeserializers.containsKey(trackType)
}
