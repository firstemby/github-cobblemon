/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.tween.track

/**
 * Tracks are stateless evaluators — they compute a value for any given time
 * without maintaining internal state. Use [TrackPlayer] to add playback
 * control (tick, pause, loop, speed).
 */
interface Track<T> {
    /** Duration in seconds. */
    val duration: Float

    fun evaluate(time: Float): T?
}
