/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model

/** A scene-unique, one-shot signal; loops and beat re-entry never fire it again. */
data class SceneCue(val id: String, val time: Float) {
    init {
        require(id.isNotBlank() && id.length <= 128) { "Cue id must contain 1 to 128 characters" }
        require(time.isFinite() && time >= 0f) { "Cue time must be finite and non-negative" }
    }
}
