/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.inspector.actortrack

import com.cobblemon.mod.common.api.cinematic.model.tracks.builtin.AnimationTrack
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AnimationTrackEditorJsonTest {

    @Test
    fun `appendCue writes JSON that AnimationTrack fromJson accepts`() {
        val data = JsonObject().apply { add("cues", JsonArray()) }
        AnimationTrackEditor.appendCue(data, time = 0.5f, name = "ground_idle")
        AnimationTrackEditor.appendCue(data, time = 1.5f, name = "attack")

        val parsed = AnimationTrack.fromJson(data)
        assertEquals(2, parsed.cues.size)
        assertEquals("attack", parsed.cues[1].animationName)
    }
}
