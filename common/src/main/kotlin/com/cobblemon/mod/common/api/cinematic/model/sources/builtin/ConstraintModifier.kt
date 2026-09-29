/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.cobblemon.mod.common.api.cinematic.model.sources.ShotModifier
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class ConstraintModifier(
    val minX: Double? = null, val maxX: Double? = null,
    val minY: Double? = null, val maxY: Double? = null,
    val minZ: Double? = null, val maxZ: Double? = null
) : ShotModifier() {
    override val type: String = TYPE

    init {
        require(listOfNotNull(minX, maxX, minY, maxY, minZ, maxZ).all { it.isFinite() }) { "Constraint bounds must be finite" }
        require(minX == null || maxX == null || minX <= maxX) { "Minimum X must not exceed maximum X" }
        require(minY == null || maxY == null || minY <= maxY) { "Minimum Y must not exceed maximum Y" }
        require(minZ == null || maxZ == null || minZ <= maxZ) { "Minimum Z must not exceed maximum Z" }
    }

    fun apply(pos: Vec3): Vec3 = Vec3(
        pos.x.let { if (minX != null && it < minX) minX else if (maxX != null && it > maxX) maxX else it },
        pos.y.let { if (minY != null && it < minY) minY else if (maxY != null && it > maxY) maxY else it },
        pos.z.let { if (minZ != null && it < minZ) minZ else if (maxZ != null && it > maxZ) maxZ else it }
    )

    companion object {
        const val TYPE = "constraint"
        fun fromJson(obj: JsonObject): ConstraintModifier = ConstraintModifier(
            minX = obj.get("minX")?.asDouble, maxX = obj.get("maxX")?.asDouble,
            minY = obj.get("minY")?.asDouble, maxY = obj.get("maxY")?.asDouble,
            minZ = obj.get("minZ")?.asDouble, maxZ = obj.get("maxZ")?.asDouble
        )
    }
}
