/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources

data class Lens(
    val aspectCrop: Float? = null,
    val letterbox: Float = 0f
) {
    init {
        aspectCrop?.let {
            require(it.isFinite() && it > 0f) { "Lens aspectCrop must be positive, got $it" }
        }
        require(letterbox >= 0f && letterbox <= 0.5f) {
            "Lens letterbox must be in [0, 0.5], got $letterbox"
        }
    }
}
