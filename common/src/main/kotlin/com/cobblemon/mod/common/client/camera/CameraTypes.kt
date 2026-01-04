/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.camera

import com.bedrockk.molang.runtime.MoLangRuntime
import com.bedrockk.molang.runtime.value.MoValue
import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.camera.CameraRotation
import com.cobblemon.mod.common.api.molang.ExpressionLike
import com.cobblemon.mod.common.util.asExpressionLike
import com.cobblemon.mod.common.util.mainThreadRuntime
import com.cobblemon.mod.common.util.resolve
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import net.minecraft.world.phys.Vec3

sealed class LookTarget {
    data class Entity(val selector: ExpressionLike) : LookTarget()
    data class Point(val position: PositionExpression) : LookTarget()

    companion object {
        fun fromJson(obj: JsonObject): LookTarget {
            val type = obj.get("type")?.asString
                ?: throw IllegalArgumentException("LookTarget missing 'type' field")

            return when (type) {
                "entity" -> {
                    val selector = obj.get("selector")?.asString
                        ?: throw IllegalArgumentException("Entity focus missing 'selector' field")
                    Entity(selector.asExpressionLike())
                }
                "position", "point" -> {
                    val posObj = obj.getAsJsonObject("position")
                        ?: obj.asJsonObject // If no nested position, use the object itself
                    Point(PositionExpression.fromJson(posObj))
                }
                else -> {
                    throw IllegalArgumentException("Unknown LookTarget type: $type")
                }
            }
        }
    }
}

data class PositionExpression(
    val x: CoordinateValue,
    val y: CoordinateValue,
    val z: CoordinateValue
) {
    companion object {
        fun fromJson(obj: JsonObject): PositionExpression {
            return PositionExpression(
                x = deserializeCoordinate(obj.get("x")),
                y = deserializeCoordinate(obj.get("y")),
                z = deserializeCoordinate(obj.get("z"))
            )
        }

        private fun deserializeCoordinate(element: JsonElement?): CoordinateValue {
            if (element == null) {
                return CoordinateValue.Static(0.0)
            }

            return when {
                element.isJsonPrimitive && element.asJsonPrimitive.isNumber -> {
                    CoordinateValue.Static(element.asDouble)
                }
                element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                    // String is treated as expression
                    CoordinateValue.Dynamic(element.asString.asExpressionLike())
                }
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    val type = obj.get("type")?.asString
                    val expression = obj.get("expression")?.asString

                    when (type) {
                        "expression" -> {
                            if (expression == null) {
                                throw IllegalArgumentException("Expression coordinate missing 'expression' field")
                            }
                            CoordinateValue.Dynamic(expression.asExpressionLike())
                        }
                        else -> {
                            throw IllegalArgumentException("Unknown coordinate type: $type")
                        }
                    }
                }
                else -> {
                    throw IllegalArgumentException("Invalid coordinate value: $element")
                }
            }
        }
    }
}

sealed class CoordinateValue {
    data class Static(val value: Double) : CoordinateValue()
    data class Dynamic(val expression: ExpressionLike) : CoordinateValue()

    fun evaluate(
        context: Map<String, MoValue>,
        runtime: MoLangRuntime = mainThreadRuntime
    ): Double {
        return when (this) {
            is Static -> value
            is Dynamic -> {
                try {
                    runtime.resolve(expression, context).asDouble()
                } catch (e: Exception) {
                    Cobblemon.LOGGER.error("[CobblemonCamera] Failed to evaluate coordinate expression: ${e.message}")
                    0.0
                }
            }
        }
    }
}

enum class PathInterpolationType {
    LINEAR,
    BEZIER,
    CUBIC_BEZIER,
    HERMITE
}

fun PositionExpression.toVec3(
    context: Map<String, MoValue> = emptyMap(),
    origin: Vec3 = Vec3.ZERO,
    isRelative: Boolean = false,
    runtime: MoLangRuntime = mainThreadRuntime
): Vec3 {
    val base = Vec3(
        x.evaluate(context, runtime),
        y.evaluate(context, runtime),
        z.evaluate(context, runtime)
    )

    return if (isRelative) {
        base.add(origin)
    } else {
        base
    }
}

data class CameraTransform(
    val position: Vec3,
    val focus: LookTarget? = null,
    val manualRotation: CameraRotation? = null
)

typealias CameraFocus = LookTarget
