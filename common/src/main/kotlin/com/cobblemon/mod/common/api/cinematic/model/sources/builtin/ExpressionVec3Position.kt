/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.cinematic.model.sources.builtin

import com.bedrockk.molang.runtime.MoLangRuntime
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.cinematic.model.sources.PositionSource
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.resolveDouble
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3
import java.util.concurrent.atomic.AtomicBoolean

data class ExpressionVec3Position(
    val xExpression: ExpressionLike,
    val yExpression: ExpressionLike,
    val zExpression: ExpressionLike,
    val fallback: Vec3 = Vec3.ZERO
) : PositionSource() {
    override val type: String = "expression_vec3"
    @Transient private val warningReported = AtomicBoolean()

    init {
        require(fallback.x.isFinite() && fallback.y.isFinite() && fallback.z.isFinite()) { "Expression position fallback must be finite" }
    }

    fun evaluate(runtime: MoLangRuntime?): Vec3 {
        if (runtime == null) return fallback
        return try {
            val x = runtime.resolveDouble(xExpression)
            val y = runtime.resolveDouble(yExpression)
            val z = runtime.resolveDouble(zExpression)
            // MoLang can return NaN or infinity without throwing, so validate before sending transforms.
            if (!x.isFinite() || !y.isFinite() || !z.isFinite()) {
                if (warningReported.compareAndSet(false, true)) {
                    Cobblemon.LOGGER.warn("[ExpressionVec3Position] Non-finite result ($x, $y, $z); using fallback $fallback")
                }
                fallback
            } else {
                Vec3(x, y, z)
            }
        } catch (e: Exception) {
            if (warningReported.compareAndSet(false, true)) {
                Cobblemon.LOGGER.warn("[ExpressionVec3Position] Evaluation failed: ${e.message}; using fallback $fallback", e)
            }
            fallback
        }
    }

    companion object {
        const val TYPE = "expression_vec3"

        fun fromJson(obj: JsonObject): ExpressionVec3Position {
            val x = obj.get("x")?.asString
                ?: throw IllegalArgumentException("ExpressionVec3Position missing 'x'")
            val y = obj.get("y")?.asString
                ?: throw IllegalArgumentException("ExpressionVec3Position missing 'y'")
            val z = obj.get("z")?.asString
                ?: throw IllegalArgumentException("ExpressionVec3Position missing 'z'")
            val fb = obj.get("fallback")?.asJsonObject
            val fallback = if (fb != null) {
                Vec3(fb.get("x").asDouble, fb.get("y").asDouble, fb.get("z").asDouble)
            } else Vec3.ZERO
            return ExpressionVec3Position(
                xExpression = x.asExpressionLike(),
                yExpression = y.asExpressionLike(),
                zExpression = z.asExpressionLike(),
                fallback = fallback
            )
        }
    }
}
