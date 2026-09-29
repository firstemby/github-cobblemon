/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

import com.cobblemon.mod.common.api.molang.ExpressionLike

/** Clamping at a beat's duration does not end the scene; completion requires an explicit transition. */
data class BeatTransition(
    val sourceBeatId: String,
    val targetBeatId: String,
    val condition: ExpressionLike,
    val transition: CutTransition = CutTransition.Blend(0.4f),
    val endsScene: Boolean = false
) {
    init {
        require(sourceBeatId.isNotBlank()) { "BeatTransition sourceBeatId must be non-blank" }
        require(endsScene || targetBeatId.isNotBlank()) { "BeatTransition targetBeatId must be non-blank" }
    }
}
