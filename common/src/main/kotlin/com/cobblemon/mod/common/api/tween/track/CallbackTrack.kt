/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

/**
 * A track that fires callbacks at specific times. Does not produce a value.
 *
 * Callbacks are one-shot: each fires exactly once when the playhead crosses
 * its time (managed by [TrackPlayer]).
 */
class CallbackTrack(
    val callbacks: List<TimedCallback>
) : Track<Unit> {

    override val duration: Float
        get() = callbacks.maxOfOrNull { it.time } ?: 0f

    override fun evaluate(time: Float): Unit = Unit
}

data class TimedCallback(
    val time: Float,
    val action: () -> Unit
)
