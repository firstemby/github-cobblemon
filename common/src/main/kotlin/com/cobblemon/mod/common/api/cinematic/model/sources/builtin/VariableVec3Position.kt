/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

data class VariableVec3Position(
    val prefix: String,
    val offset: Vec3 = Vec3.ZERO,
    val fallback: Vec3 = Vec3.ZERO
) : PositionSource() {
    override val type: String = "variable_vec3"

    init {
        require(listOf(offset, fallback, fallback.add(offset)).all { it.x.isFinite() && it.y.isFinite() && it.z.isFinite() }) {
            "Variable position offset and fallback must be finite"
        }
    }

    fun evaluate(runtime: MoLangRuntime?): Vec3 {
        if (runtime == null) return fallback.add(offset)
        val vars = runtime.environment.variable.map
        return runCatching {
            val x = vars["${prefix}_x"]?.asDouble() ?: return fallback.add(offset)
            val y = vars["${prefix}_y"]?.asDouble() ?: return fallback.add(offset)
            val z = vars["${prefix}_z"]?.asDouble() ?: return fallback.add(offset)
            Vec3(x + offset.x, y + offset.y, z + offset.z).takeIf {
                it.x.isFinite() && it.y.isFinite() && it.z.isFinite()
            } ?: fallback.add(offset)
        }.getOrElse { fallback.add(offset) }
    }

    companion object {
        const val TYPE = "variable_vec3"
        fun fromJson(obj: JsonObject): VariableVec3Position {
            val prefix = obj.get("prefix")?.asString
                ?: throw IllegalArgumentException("VariableVec3Position missing 'prefix'")
            val off = obj.get("offset")?.asJsonObject
            val offset = if (off != null) {
                Vec3(off.get("x").asDouble, off.get("y").asDouble, off.get("z").asDouble)
            } else Vec3.ZERO
            val fb = obj.get("fallback")?.asJsonObject
            val fallback = if (fb != null) {
                Vec3(fb.get("x").asDouble, fb.get("y").asDouble, fb.get("z").asDouble)
            } else Vec3.ZERO
            return VariableVec3Position(prefix, offset, fallback)
        }
    }
}
