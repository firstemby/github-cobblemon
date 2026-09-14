/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.editor.cinematic.panel.timeline

import com.cobblemon.mod.common.client.editor.model.cinematic.EditorBeat

// An automatic beat is not zero-length; its timeline must remain usable without an explicit duration.
object TimelineBeatSpan {

    const val MINIMUM_SECONDS = 10f
    const val HEADROOM_SECONDS = 2f

    fun contentEnd(beat: EditorBeat): Float {
        var end = 0f
        beat.cues.forEach { if (it.time > end) end = it.time }
        beat.cuts.values.forEach { track ->
            track.entries.forEach { if (it.time > end) end = it.time }
        }
        beat.actorDirections.values.forEach { direction ->
            direction.tracks.forEach { track ->
                TimelineTrackTimes.times(track).forEach { if (it > end) end = it }
            }
        }
        return end
    }

    // Leave headroom after the last item so the final cut has a visible width.
    fun spanOf(beat: EditorBeat): Float =
        beat.duration ?: maxOf(contentEnd(beat) + HEADROOM_SECONDS, MINIMUM_SECONDS)
}
