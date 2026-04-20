/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.camera.track

import com.cobblemon.mod.common.api.camera.CameraTrackRegistry
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests for CameraTrackDeserializers to ensure proper JSON parsing.
 */
class CameraTrackDeserializersTest {

    init {
        CameraTrackDeserializers.registerBuiltinTracks()
    }

    @Test
    fun `deserialize position track with object format`() {
        val json = """
            {
                "trackType": "position",
                "keyframes": [{"time": 0.0, "position": {"x": 10.0, "y": 64.0, "z": 10.0}}]
            }
        """.trimIndent()

        val track = CameraTrackRegistry.deserializeTrack("position", JsonParser.parseString(json).asJsonObject)
        assertNotNull(track)
        assertEquals("position", track!!.trackType)
    }

    @Test
    fun `deserialize position track with array format`() {
        val json = """
            {
                "trackType": "position",
                "keyframes": [{"time": 0.0, "position": [10.0, 64.0, 10.0]}]
            }
        """.trimIndent()

        val track = CameraTrackRegistry.deserializeTrack("position", JsonParser.parseString(json).asJsonObject)
        assertNotNull(track)
    }

    @Test
    fun `deserialize focus track with bone target using array offset`() {
        val json = """
            {
                "trackType": "focus",
                "keyframes": [{
                    "time": 0.0,
                    "target": {
                        "type": "bone",
                        "selector": "q.get_entity('pokemon')",
                        "bone": "head",
                        "offset": [0.0, 1.0, 0.0]
                    }
                }]
            }
        """.trimIndent()

        val track = CameraTrackRegistry.deserializeTrack("focus", JsonParser.parseString(json).asJsonObject)
        assertNotNull(track)
        assertEquals("focus", track!!.trackType)
    }

    @Test
    fun `deserialize focus track with bone target using object offset`() {
        val json = """
            {
                "trackType": "focus",
                "keyframes": [{
                    "time": 0.0,
                    "target": {
                        "type": "bone",
                        "selector": "q.get_entity('pokemon')",
                        "boneName": "head",
                        "offset": {"x": 0.0, "y": 1.0, "z": 0.0}
                    }
                }]
            }
        """.trimIndent()

        val track = CameraTrackRegistry.deserializeTrack("focus", JsonParser.parseString(json).asJsonObject)
        assertNotNull(track)
    }

    @Test
    fun `registered track types are available`() {
        val types = CameraTrackRegistry.getRegisteredTrackTypes()
        assertTrue(types.contains("position"))
        assertTrue(types.contains("focus"))
        assertTrue(types.contains("event"))
        assertTrue(types.contains("reference"))
    }
}
