/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.FovSource
import com.google.gson.JsonObject

data class FixedFov(val fov: Float) : FovSource() {
    override val type: String = "fixed"

    init {
        require(fov in 1f..179f) { "FixedFov must be in [1, 179], got $fov" }
    }

    companion object {
        const val TYPE = "fixed"
        fun fromJson(obj: JsonObject): FixedFov = FixedFov(obj.get("value").asFloat)
    }
}
