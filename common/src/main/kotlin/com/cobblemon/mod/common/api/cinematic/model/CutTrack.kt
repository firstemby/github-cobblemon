/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

data class CutTrack(
    val entries: List<CutEntry>
) {
    init {
        require(entries.isNotEmpty()) { "CutTrack must have at least one entry" }
        require(entries.zipWithNext().all { (a, b) -> a.time <= b.time }) {
            "CutTrack entries must be sorted ascending by time"
        }
    }
}

data class CutEntry(
    val time: Float,
    val shotId: String,
    val transition: CutTransition = CutTransition.Instant
) {
    init {
        require(time.isFinite() && time >= 0f) { "CutEntry time must be non-negative, got $time" }
        require(shotId.isNotBlank()) { "CutEntry shotId must be non-blank" }
    }
}

sealed class CutTransition {
    object Instant : CutTransition()

    data class Blend(val duration: Float) : CutTransition() {
        init {
            require(duration.isFinite() && duration > 0f) { "Blend duration must be positive, got $duration" }
        }
    }
}
