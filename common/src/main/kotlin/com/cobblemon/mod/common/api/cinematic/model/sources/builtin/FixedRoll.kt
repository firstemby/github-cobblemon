/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.RollSource
import com.google.gson.JsonObject

data class FixedRoll(val roll: Float) : RollSource() {
    override val type: String = "fixed"

    init {
        require(roll.isFinite()) { "Fixed roll must be finite" }
    }

    companion object {
        const val TYPE = "fixed"
        fun fromJson(obj: JsonObject): FixedRoll = FixedRoll(obj.get("value").asFloat)
    }
}
