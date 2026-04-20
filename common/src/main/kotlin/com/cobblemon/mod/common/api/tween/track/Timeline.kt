/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

class SequentialTimeline<T>(
    private val tracks: List<Track<T>>
) : Track<T> {

    override val duration: Float
        get() = tracks.sumOf { it.duration.toDouble() }.toFloat()

    override fun evaluate(time: Float): T? {
        var accumulated = 0f
        for (track in tracks) {
            if (time < accumulated + track.duration) {
                return track.evaluate(time - accumulated)
            }
            accumulated += track.duration
        }
        return tracks.lastOrNull()?.let { it.evaluate(it.duration) }
    }
}

class ParallelTimeline<T>(
    private val tracks: List<Track<T>>
) : Track<List<T?>> {

    override val duration: Float
        get() = tracks.maxOfOrNull { it.duration } ?: 0f

    override fun evaluate(time: Float): List<T?> {
        return tracks.map { it.evaluate(time) }
    }
}
