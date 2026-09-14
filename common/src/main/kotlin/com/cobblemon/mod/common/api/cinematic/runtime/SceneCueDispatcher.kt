/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.runtime

import com.cobblemon.mod.common.api.cinematic.model.Beat
import com.cobblemon.mod.common.api.cinematic.model.SceneCue

class SceneCueDispatcher {
    private val fired = mutableSetOf<String>()

    fun dispatch(beat: Beat, time: Float, isActive: () -> Boolean, receive: (SceneCue) -> Unit) {
        val sampledTime = beat.duration?.let { time.coerceAtMost(it) } ?: time
        for (cue in beat.cues.sortedBy { it.time }) {
            if (!isActive()) return
            if (cue.time <= sampledTime && fired.add(cue.id)) receive(cue)
        }
    }
}
