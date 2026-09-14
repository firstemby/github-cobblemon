/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class FixedPosition(val position: Vec3) : PositionSource() {
    override val type: String = "fixed"

    init {
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) { "Fixed position must be finite" }
    }

    companion object {
        const val TYPE = "fixed"
        fun fromJson(obj: JsonObject): FixedPosition {
            val pos = obj.getAsJsonObject("position") ?: throw IllegalArgumentException("FixedPosition missing 'position'")
            return FixedPosition(Vec3(pos.get("x").asDouble, pos.get("y").asDouble, pos.get("z").asDouble))
        }
    }
}
