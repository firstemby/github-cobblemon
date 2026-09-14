/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

data class FeedDeclaration(
    val id: String,
    val kind: FeedKind
) {
    init {
        require(id.isNotBlank()) { "FeedDeclaration id must be non-blank" }
    }

    companion object {
        /** Bound for feed counts read off the wire, so a crafted packet can't drive a huge allocation. */
        const val MAX_FEEDS = 64
    }
}

sealed class FeedKind {
    /** Occupies the full viewport. */
    object Fullscreen : FeedKind()

    /** Picture-in-picture overlay in a viewport corner. [size] is a fraction of viewport. */
    data class Pip(val corner: Corner, val size: Float) : FeedKind() {
        init {
            require(size > 0f && size <= 1f) { "Pip size must be in (0, 1], got $size" }
        }
    }

    /** Relative share of a paired split. */
    data class Split(val side: Side, val size: Float) : FeedKind() {
        init {
            require(size > 0f && size <= 1f) { "Split size must be in (0, 1], got $size" }
        }
    }

    data class Diagonal(val half: DiagonalHalf) : FeedKind()

    enum class Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }
    enum class Side { LEFT, RIGHT, TOP, BOTTOM }
    enum class DiagonalHalf { UPPER, LOWER }
}
